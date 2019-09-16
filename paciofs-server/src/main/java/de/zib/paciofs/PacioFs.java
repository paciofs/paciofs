/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs;

import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.cluster.Cluster;
import akka.event.Logging;
import akka.grpc.javadsl.ServiceHandler;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Function;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import de.zib.paciofs.grpc.PacioFsGrpcUtil;
import de.zib.paciofs.grpc.PacioFsServiceImpl;
import de.zib.paciofs.grpc.PacioFsServicePowerApiHandlerFactory;
import de.zib.paciofs.io.posix.grpc.PosixIoServiceImpl;
import de.zib.paciofs.io.posix.grpc.PosixIoServicePowerApiHandlerFactory;
import de.zib.paciofs.logging.LogbackPropertyDefiners;
import de.zib.paciofs.logging.Markers;
import de.zib.paciofs.multichain.MultiChainClientFactory;
import de.zib.paciofs.multichain.abstractions.MultiChainCluster;
import de.zib.paciofs.multichain.abstractions.MultiChainFileSystem;
import de.zib.paciofs.multichain.actors.MultiChainActor;
import de.zib.paciofs.multichain.rpc.MultiChainClient;
import de.zib.paciofs.multichain.rpc.types.BlockChainInfo;
import de.zib.paciofs.multichain.rpc.types.MultiChainException;
import de.zib.paciofs.multichain.rpc.types.UnspentTransactionOutputList;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.JavaConverters;

public class PacioFs {
  private static Logger log;

  private PacioFs() {}

  /**
   * Starts PacioFs and waits for shutdown.
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    // prints error, help and exits if necessary
    final PacioFsCliOptions cliOptions = parseCommandLine(args);

    // parses application.conf from the classpath
    final Config applicationConfig = ConfigFactory.load();

    // if the user has supplied a configuration, use the default configuration only as a fallback
    // for missing options (i.e. the user configuration wins)
    final Config config;
    if (cliOptions.getConfig() != null) {
      config = ConfigFactory.parseFile(cliOptions.getConfig()).withFallback(applicationConfig);
    } else {
      config = applicationConfig;
    }

    // no logging is allowed to happen before here
    initializeLogging(config);

    // log invocation for later inspection
    log.info("Arguments: {}", String.join(" ", args));

    // the entire Akka configuration is a bit overwhelming
    log.debug(Markers.CONFIGURATION, "Using configuration: {}", config);

    // create the actor system
    final ActorSystem paciofs = ActorSystem.create("paciofs", config);

    // reset Akka logging if necessary
    initializeAkkaLogging(paciofs);

    // if we appear to be in a k8s environment, do the bootstrapping
    if (System.getenv("KUBERNETES_SERVICE_HOST") != null) {
      log.info("Bootstrapping using k8s");

      // hosts HTTP routes used by bootstrap
      AkkaManagement.get(paciofs).start();

      // starts dynamic bootstrapping
      ClusterBootstrap.get(paciofs).start();
    }

    final Cluster cluster = Cluster.get(paciofs);
    log.info("Started [{}], cluster.selfAddress = {}", paciofs, cluster.selfAddress());

    // MultiChain client
    final MultiChainClient multiChainClient = initializeMultiChainClient(paciofs);
    waitForUtxos(multiChainClient);

    // cluster as seen by received transactions on MultiChain
    final MultiChainCluster multiChainCluster = new MultiChainCluster(multiChainClient);

    // file system as seen by received transactions on MultiChain
    final MultiChainFileSystem multiChainFileSystem = new MultiChainFileSystem(
        multiChainClient, multiChainCluster, config.getString(PacioFsOptions.BASE_DIR_KEY));

    // have MultiChain react to cluster events
    paciofs.actorOf(
        MultiChainActor.props(multiChainClient, multiChainCluster, multiChainFileSystem),
        "multichain");

    // serve the default services
    bindAndHandleAsync(Http.get(paciofs), config, paciofs, multiChainFileSystem);
  }

  /* Utility functions */

  private static void bindAndHandleAsync(
      Http http, Config config, ActorSystem system, MultiChainFileSystem multiChainFileSystem) {
    final Materializer materializer = ActorMaterializer.create(system);

    // concat the handlers
    final List<Function<HttpRequest, CompletionStage<HttpResponse>>> handlers = new ArrayList<>();
    handlers.add(PacioFsServicePowerApiHandlerFactory.create(
        new PacioFsServiceImpl(multiChainFileSystem), materializer, system));
    handlers.add(PosixIoServicePowerApiHandlerFactory.create(
        new PosixIoServiceImpl(multiChainFileSystem), materializer, system));
    final Function<HttpRequest, CompletionStage<HttpResponse>> combinedHandler =
        ServiceHandler.concatOrNotFound(JavaConverters.collectionAsScalaIterable(handlers).toSeq());

    // set up HTTP if desired
    try {
      PacioFsGrpcUtil.bindAndHandleAsyncHttp(combinedHandler, http,
          config.getString(PacioFsOptions.HTTP_BIND_HOSTNAME_KEY),
          config.getInt(PacioFsOptions.HTTP_BIND_PORT_KEY), materializer);
    } catch (ConfigException.Missing | ConfigException.WrongType e) {
      log.info("No valid HTTP configuration found, not serving HTTP ({})", e.getMessage());
    }

    // set up HTTPS if desired
    try {
      String caCertPath = null;
      String caCertPassPath = null;
      try {
        caCertPath = config.getString(PacioFsOptions.HTTPS_CA_CERT_PATH_KEY);
        caCertPassPath = config.getString(PacioFsOptions.HTTPS_CA_CERT_PASS_PATH_KEY);
      } catch (ConfigException.Missing e) {
        // tolerate missing CA certificates, falls back to system
      }

      PacioFsGrpcUtil.bindAndHandleAsyncHttps(combinedHandler, http,
          config.getString(PacioFsOptions.HTTPS_BIND_HOSTNAME_KEY),
          config.getInt(PacioFsOptions.HTTPS_BIND_PORT_KEY), materializer,
          PacioFsGrpcUtil.httpsConnectionContext(
              config.getString(PacioFsOptions.HTTPS_SERVER_CERT_PATH_KEY),
              config.getString(PacioFsOptions.HTTPS_SERVER_CERT_PASS_PATH_KEY), caCertPath,
              caCertPassPath));
    } catch (ConfigException.Missing | ConfigException.WrongType e) {
      log.info("No valid HTTPS configuration found, not serving HTTPS ({})", e.getMessage());
    } catch (GeneralSecurityException | IOException e) {
      log.error("{}: not serving HTTPS", e.getMessage());
      log.error(Markers.EXCEPTION, e.getMessage(), e);
    }
  }

  private static void initializeAkkaLogging(ActorSystem system) {
    final Config config = system.settings().config();

    // Akka loggers get started with the default application.conf, so reset the log level in case
    // the user has changed it
    try {
      system.eventStream().setLogLevel(
          Logging.levelFor(config.getString(AkkaOptions.LOG_LEVEL_KEY)).get().asInt());
    } catch (ConfigException.Missing e) {
      // the user has not specified a custom log level
    } catch (NoSuchElementException e) {
      log.warn("Invalid Akka log level: {}", config.getString(AkkaOptions.LOG_LEVEL_KEY));
    }
  }

  private static void initializeLogging(Config config) {
    // supply our configuration to Logback
    LogbackPropertyDefiners.ConfigVarWithDefaultValue.setConfig(config);

    // now trigger initialization of Logback
    log = LoggerFactory.getLogger(PacioFs.class);
  }

  private static MultiChainClient initializeMultiChainClient(ActorSystem system) {
    final MultiChainClient multiChainClient = new MultiChainClientFactory(
        system.settings().config().getConfig(PacioFsOptions.MULTICHAIN_CLIENT_KEY))
                                                  .create();

    // shut down MultiChain client before the actor system
    CoordinatedShutdown.get(system).addJvmShutdownHook(multiChainClient::stop);

    // warm up the client
    final BlockChainInfo info = multiChainClient.getBlockChainInfo();
    log.info("Connected to MultiChain: {}", info.chain());

    return multiChainClient;
  }

  private static void waitForUtxos(MultiChainClient multiChainClient) {
    final long backoffMilliseconds = 50;
    final int maxAttempts = 10;
    final int utxoMinConfirmations = 0;

    int unspent = 0;
    for (int attempt = 0; attempt < maxAttempts; ++attempt) {
      try {
        log.info("Waiting for UTXOs, attempt {} of {}", attempt + 1, maxAttempts);
        final UnspentTransactionOutputList utxos =
            multiChainClient.listUnspent(utxoMinConfirmations);
        unspent = utxos.size();
      } catch (MultiChainException e) {
        // handle the same way as if zero UTXOs had been returned
      }

      if (unspent > 0) {
        log.info("Got {} UTXO(s)", unspent);
        break;
      }

      try {
        // exponential backoff that doubles with every attempt
        Thread.sleep(backoffMilliseconds * (long) Math.pow(2, attempt));
      } catch (InterruptedException e) {
        log.warn("Interrupted while waiting for UTXOs", e);
      }
    }

    if (unspent == 0) {
      throw new RuntimeException("No UTXOs after " + maxAttempts + " attempts");
    }
  }

  private static PacioFsCliOptions parseCommandLine(String[] args) {
    final PacioFsCliOptions cliOptions = new PacioFsCliOptions();

    // exit with 1 on error
    if (!cliOptions.parseCommandLine(args)) {
      cliOptions.printError();
      cliOptions.printHelp();
      System.exit(1);
    }

    // exit with 0 on help
    if (cliOptions.getHelp()) {
      cliOptions.printHelp();
      System.exit(0);
    }

    return cliOptions;
  }
}
