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
import de.zib.paciofs.blockchain.PFSBlockchain;
import de.zib.paciofs.cluster.PFSCluster;
import org.apache.commons.cli.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public class PacioFS {

  public static void main(String[] args) {
    final CommandLine cmd = parseCommandLine(args);
    final boolean skipBootstrap = cmd.hasOption("skip-bootstrap");

    // parses application.conf from the classpath
    // exclude bootstrapping configuration if requested (e.g. if we are not
    // running in kubernetes)
    final Config config = skipBootstrap
                              ? ConfigFactory.load().withoutPath(
                                    "akka.management.cluster.bootstrap")
                              : ConfigFactory.load();

    // create the actor system
    final ActorSystem paciofs = ActorSystem.create("paciofs", config);

    Materializer mat = ActorMaterializer.create(paciofs);
    Cluster cluster = Cluster.get(paciofs);

    paciofs.log().info("Started [" + paciofs + "], cluster.selfAddress = " +
                       cluster.selfAddress() + ")");

    // again, skip bootstrapping if requested
    if (!skipBootstrap) {
      // hosts HTTP routes used by bootstrap
      AkkaManagement.get(paciofs).start();

      // starts dynamic bootstrapping
      ClusterBootstrap.get(paciofs).start();
    }

    // listens to cluster events
    paciofs.actorOf(PFSCluster.props(), "pfsCluster");

    // actor for the blockchain
    paciofs.actorOf(PFSBlockchain.props(), "pfsBlockchain");

    // figure out where we are
    String hostAddress;
    try {
      hostAddress = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      hostAddress = "<unknown host>";
    }

    Http.get(paciofs).bindAndHandle(
        Directives.complete("paciofs@" + hostAddress).flow(paciofs, mat),
        ConnectHttp.toHost("0.0.0.0", 8080), mat);
  }

  private static CommandLine parseCommandLine(String[] args) {
    final Options options = new Options();
    options.addOption("h", "help", false, "print this message and exit");

    options.addOption(
        "s", "skip-bootstrap", false,
        "whether to skip bootstrapping (e.g. when outside kubernetes)");

    final CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;

    // 0 on help option, 1 on error, -1 otherwise
    int exitCode;
    try {
      cmd = parser.parse(options, args, false);

      // exit on unrecognized arguments or help option
      List<String> argList = cmd.getArgList();
      if (argList.size() > 0) {
        System.err.println("Unrecognized argument(s): " +
                           String.join(" ", argList));
        exitCode = 1;
      } else {
        exitCode = cmd.hasOption("help") ? 0 : -1;
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
}
