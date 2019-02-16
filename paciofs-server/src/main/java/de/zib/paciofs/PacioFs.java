/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.event.Logging;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.UseHttp2;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.Function;
import akka.management.AkkaManagement;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.TLSClientAuth;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import de.zib.paciofs.blockchain.Bitcoind;
import de.zib.paciofs.io.posix.grpc.PosixIoServiceHandlerFactory;
import de.zib.paciofs.io.posix.grpc.PosixIoServiceImpl;
import de.zib.paciofs.logging.LogbackPropertyDefiners;
import de.zib.paciofs.logging.Markers;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // actor for the blockchain
    paciofs.actorOf(Bitcoind.props(), "bitcoind");

    // get ready to serve the I/O service
    final Http http = Http.get(paciofs);
    final Materializer mat = ActorMaterializer.create(paciofs);
    final Function<HttpRequest, CompletionStage<HttpResponse>> posixIoHandler =
        PosixIoServiceHandlerFactory.create(new PosixIoServiceImpl(), mat);

    // set up HTTP if desired
    try {
      http.bindAndHandleAsync(posixIoHandler,
              ConnectHttp.toHost(config.getString(PacioFsOptions.HTTP_BIND_HOSTNAME_KEY),
                  config.getInt(PacioFsOptions.HTTP_BIND_PORT_KEY), UseHttp2.always()),
              mat)
          .thenAccept(binding
              -> log.info("{} gRPC HTTP server bound to: {}",
                  PosixIoServiceImpl.class.getSimpleName(), binding.localAddress()));
    } catch (ConfigException.Missing | ConfigException.WrongType e) {
      log.info("No valid HTTP configuration found, not serving HTTP ({})", e.getMessage());
    }

    // set up HTTPS if desired
    try {
      final HttpsConnectionContext https = httpsConnectionContext(config);
      http.bindAndHandleAsync(posixIoHandler,
              ConnectHttp
                  .toHostHttps(config.getString(PacioFsOptions.HTTPS_BIND_HOSTNAME_KEY),
                      config.getInt(PacioFsOptions.HTTPS_BIND_PORT_KEY))
                  .withCustomHttpsContext(https),
              mat)
          .thenAccept(binding
              -> log.info("{} gRPC HTTPS server bound to: {}",
                  PosixIoServiceImpl.class.getSimpleName(), binding.localAddress()));
    } catch (ConfigException.Missing | ConfigException.WrongType e) {
      log.info("No valid HTTPS configuration found, not serving HTTPS ({})", e.getMessage());
    } catch (GeneralSecurityException | IOException e) {
      log.error("{}: not serving HTTPS", e.getMessage());
      log.error(Markers.EXCEPTION, e.getMessage(), e);
    }
  }

  /* Utility functions */

  private static HttpsConnectionContext httpsConnectionContext(Config config)
      throws GeneralSecurityException, IOException {
    final String algorithm = "SunX509";
    final String protocol = "TLS";
    final String type = "PKCS12";

    // initialize key manager (contains our certificates)
    final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
    char[] password =
        readPasswordFromFile(config.getString(PacioFsOptions.HTTPS_SERVER_CERT_PASS_PATH_KEY));
    keyManagerFactory.init(
        readKeyStoreFromFile(
            config.getString(PacioFsOptions.HTTPS_SERVER_CERT_PATH_KEY), password, type),
        password);

    // get the context
    final SSLContext sslContext = SSLContext.getInstance(protocol);

    try {
      // initialize trust manager (contains trusted certificates)
      final TrustManagerFactory trustManagerFactory;
      password = readPasswordFromFile(config.getString(PacioFsOptions.HTTPS_CA_CERT_PASS_PATH_KEY));
      trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
      trustManagerFactory.init(readKeyStoreFromFile(
          config.getString(PacioFsOptions.HTTPS_CA_CERT_PATH_KEY), password, type));

      sslContext.init(
          keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
    } catch (ConfigException.Missing e) {
      // tolerate missing trust manager configuration
      log.info("Using system trust managers because {} and/or {} were not set",
          PacioFsOptions.HTTPS_CA_CERT_PATH_KEY, PacioFsOptions.HTTPS_CA_CERT_PASS_PATH_KEY);

      sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
    }

    // build the context, requiring mutual authentication
    return ConnectionContext.https(sslContext, Optional.empty(), Optional.empty(),
        Optional.of(TLSClientAuth.need()), Optional.empty());
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

  private static KeyStore readKeyStoreFromFile(String path, char[] password, String type)
      throws GeneralSecurityException, IOException {
    final KeyStore keyStore = KeyStore.getInstance(type);
    try (InputStream p12 = new FileInputStream(path)) {
      keyStore.load(p12, password);
    }

    return keyStore;
  }

  private static char[] readPasswordFromFile(String path) throws IOException {
    final char[] password;
    try (BufferedReader passReader = new BufferedReader(
             new InputStreamReader(new FileInputStream(path), Charset.forName("UTF-8")))) {
      final String pass = passReader.readLine();
      if (pass == null) {
        throw new IllegalArgumentException(path + " is empty");
      }

      password = new char[pass.length()];
      pass.getChars(0, pass.length(), password, 0);
    }

    return password;
  }
}
