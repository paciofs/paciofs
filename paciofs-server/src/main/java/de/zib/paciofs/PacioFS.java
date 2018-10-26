/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import de.zib.paciofs.cluster.PFSClusterListener;

public class PacioFS {
  public static void main(String[] args) {
    // parses application.conf from the classpath
    final Config config = ConfigFactory.load();

    // create the actor system
    final ActorSystem paciofs = ActorSystem.create("paciofs", config);

    // listens to cluster events
    paciofs.actorOf(PFSClusterListener.props(), "pfsClusterListener");
  }
}
