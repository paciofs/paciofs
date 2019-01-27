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
import akka.http.javadsl.Http;
import akka.http.javadsl.server.Directives;
import akka.management.AkkaManagement;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import de.zib.paciofs.blockchain.Bitcoind;
import de.zib.paciofs.logging.LogbackPropertyDefiners;
import de.zib.paciofs.logging.Markers;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacioFS {
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

    // figure out where we are
    String hostAddress;
    try {
      hostAddress = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      hostAddress = "<unknown host>";
    }

    final Materializer mat = ActorMaterializer.create(paciofs);
    Http.get(paciofs).bindAndHandle(
        Directives.complete("paciofs@" + hostAddress).flow(paciofs, mat),
        ConnectHttp.toHost("0.0.0.0", 8080), mat);
  }

  private static CommandLine parseCommandLine(String[] args) {
    final Options options = new Options();
    options.addOption(OPTION_HELP_SHORT, OPTION_HELP, false, "print this message and exit");

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
}
