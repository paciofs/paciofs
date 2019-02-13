/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.ConnectWithHttps;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacioFS {
  private static final String OPTION_BIND_HOST = "host";
  private static final String OPTION_BIND_HOST_SHORT = "o";
  private static final String OPTION_BIND_PORT = "port";
  private static final String OPTION_BIND_PORT_SHORT = "p";
  private static final String OPTION_CONFIG = "config";
  private static final String OPTION_CONFIG_SHORT = "c";
  private static final String OPTION_HELP = "help";
  private static final String OPTION_HELP_SHORT = "h";
  private static final String OPTION_SKIP_BOOTSTRAP = "skip-bootstrap";
  private static final String OPTION_SKIP_BOOTSTRAP_SHORT = "s";

  private static Logger log;

  private PacioFS() {}

  /**
   * Starts PacioFS and waits for shutdown.
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    final CommandLine cmd = parseCommandLine(args);
    final boolean skipBootstrap = cmd.hasOption(OPTION_SKIP_BOOTSTRAP);

    // parses application.conf from the classpath
    // exclude bootstrapping configuration if requested (e.g. if we are not
    // running in kubernetes)
    final Config applicationConfig = skipBootstrap
        ? ConfigFactory.load().withoutPath("akka.management.cluster.bootstrap")
        : ConfigFactory.load();

    // if the user has supplied a configuration, use the default configuration only as a fallback
    // for missing options (i.e. the user configuration wins)
    final Config config;
    if (cmd.hasOption(OPTION_CONFIG)) {
      config = ConfigFactory.parseFile(new File(cmd.getOptionValue(OPTION_CONFIG)))
                   .withFallback(applicationConfig);
    } else {
      config = applicationConfig;
    }

    // connection options
    final String host = cmd.getOptionValue(OPTION_BIND_HOST, "0.0.0.0");
    final int port;
    try {
      port = Integer.parseInt(cmd.getOptionValue(OPTION_BIND_PORT, "8080"));
    } catch (NumberFormatException e) {
      System.err.println(e.getMessage());
      System.exit(1);

      // because port is final, and javac does not know that System.exit() never returns
      return;
    }

    // no logging is allowed to happen before here
    initializeLogging(config);

    // the entire Akka configuration is a bit overwhelming
    log.debug(Markers.CONFIGURATION, "Using configuration: {}", config);

    // create the actor system
    final ActorSystem paciofs = ActorSystem.create("paciofs", config);

    // again, skip bootstrapping if requested
    if (!skipBootstrap) {
      // hosts HTTP routes used by bootstrap
      AkkaManagement.get(paciofs).start();

      // starts dynamic bootstrapping
      ClusterBootstrap.get(paciofs).start();
    }

    final Cluster cluster = Cluster.get(paciofs);
    log.info("Started [{}], cluster.selfAddress = {}", paciofs, cluster.selfAddress());

    // actor for the blockchain
    paciofs.actorOf(Bitcoind.props(), "bitcoind");

    final Materializer mat = ActorMaterializer.create(paciofs);

    // set up HTTPS if desired
    final ConnectHttp connect;
    final HttpsConnectionContext https = httpsConnectionContext(config);
    if (https != null) {
      connect = ConnectWithHttps.toHostHttps(host, port).withCustomHttpsContext(https);
    } else {
      connect = ConnectHttp.toHost(host, port);
    }

    // bind the POSIX IO service to all interfaces (0.0.0.0)
    // TODO should we bind to all interfaces?
    Http.get(paciofs)
        .bindAndHandleAsync(
            PosixIoServiceHandlerFactory.create(new PosixIoServiceImpl(), mat), connect, mat)
        .thenAccept(binding -> {
          log.info("{} gRPC server bound to: {}", PosixIoServiceImpl.class.getSimpleName(),
              binding.localAddress());
        });
  }

  private static CommandLine parseCommandLine(String[] args) {
    final Options options = new Options();
    options.addOption(OPTION_HELP_SHORT, OPTION_HELP, false, "print this message and exit");

    options.addOption(
        OPTION_BIND_HOST_SHORT, OPTION_BIND_HOST, true, "interface to bind to (default 0.0.0.0)");
    options.addOption(
        OPTION_BIND_PORT_SHORT, OPTION_BIND_PORT, true, "port to bind to (default 8080)");
    options.addOption(OPTION_CONFIG_SHORT, OPTION_CONFIG, true, "path/to/paciofs.conf");
    options.addOption(OPTION_SKIP_BOOTSTRAP_SHORT, OPTION_SKIP_BOOTSTRAP, false,
        "whether to skip bootstrapping (e.g. when outside kubernetes)");

    final CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;

    // 0 on help option, 1 on error, -1 otherwise
    int exitCode;
    try {
      cmd = parser.parse(options, args, false);

      // exit on unrecognized arguments or help option
      final List<String> argList = cmd.getArgList();
      if (argList.size() > 0) {
        System.err.println("Unrecognized argument(s): " + String.join(" ", argList));
        exitCode = 1;
      } else {
        exitCode = cmd.hasOption(OPTION_HELP) ? 0 : -1;
      }
    } catch (ParseException e) {
      // exit on parsing error
      System.err.println(e.getMessage());
      exitCode = 1;
    }

    if (exitCode >= 0) {
      final HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("PacioFS", options);
      System.exit(exitCode);
    }

    return cmd;
  }

  private static void initializeLogging(Config config) {
    // supply our configuration to Logback
    LogbackPropertyDefiners.ConfigVarWithDefaultValue.setConfig(config);

    // now trigger initialization of Logback
    log = LoggerFactory.getLogger(PacioFS.class);
  }

  private static HttpsConnectionContext httpsConnectionContext(Config config) {
    try {
      // obtain the PKCS12 archive containing all certificates
      final InputStream p12 = new FileInputStream(config.getString("paciofs.tls.certs.path"));

      // obtain the password to read the archive
      final String pass =
          new BufferedReader(new FileReader(config.getString("paciofs.tls.certs.pass-path")))
              .readLine();
      final char[] password = new char[pass.length()];
      pass.getChars(0, pass.length(), password, 0);

      // load the certificates
      final KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(p12, password);

      // initialize factories
      final String algorithm = "SunX509";
      final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
      keyManagerFactory.init(keyStore, password);
      final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
      trustManagerFactory.init(keyStore);

      // finally get the context
      final SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(),
          new SecureRandom());

      // once we made it to this point, we are good to go
      log.info("Using TLS protocol: {}", sslContext.getProtocol());
      return ConnectionContext.https(sslContext, Optional.empty(), Optional.empty(),
          Optional.of(TLSClientAuth.none()), Optional.empty());
    } catch (ConfigException.Missing e) {
      log.warn("Incomplete TLS configuration ({}), falling back to no TLS", e.getMessage());
      log.warn(Markers.EXCEPTION, "Incomplete TLS configuration", e);
    } catch (FileNotFoundException e) {
      log.warn("File not found ({}), falling back to no TLS", e.getMessage());
      log.warn(Markers.EXCEPTION, "File not found", e);
    } catch (IOException e) {
      log.warn("I/O exception occurred ({}), falling back to no TLS", e.getMessage());
      log.warn(Markers.EXCEPTION, "I/O exception", e);
    } catch (CertificateException | KeyManagementException | KeyStoreException
        | NoSuchAlgorithmException | UnrecoverableKeyException e) {
      log.warn("Exception while configuring HTTPS ({}), falling back to no TLS", e.getMessage());
      log.warn(Markers.EXCEPTION, "Exception while configuring HTTPS", e);
    }

    return null;
  }
}
