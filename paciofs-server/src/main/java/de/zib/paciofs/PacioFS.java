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
import de.zib.paciofs.cluster.PFSClusterListener;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class PacioFS {

  public static void main(String[] args) {
    // parses application.conf from the classpath
    final Config config = ConfigFactory.load();

    // create the actor system
    final ActorSystem paciofs = ActorSystem.create("paciofs", config);

    Materializer mat = ActorMaterializer.create(paciofs);
    Cluster cluster = Cluster.get(paciofs);

    paciofs.log().info("Started [" + paciofs + "], cluster.selfAddress = " +
                       cluster.selfAddress() + ")");

    // hosts HTTP routes used by bootstrap
    AkkaManagement.get(paciofs).start();

    // starts dynamic bootstrapping
    ClusterBootstrap.get(paciofs).start();

    // listens to cluster events
    paciofs.actorOf(PFSClusterListener.props(), "pfsClusterListener");

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
}
