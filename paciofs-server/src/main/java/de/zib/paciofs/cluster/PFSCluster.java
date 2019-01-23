/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.cluster;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * Listens to cluster events.
 */
public class PFSCluster extends AbstractActor {
  private final LoggingAdapter log;

  private final Cluster cluster;

  private PFSCluster() {
    this.log = Logging.getLogger(this.getContext().system(), this);
    this.cluster = Cluster.get(this.getContext().system());
  }

  public static Props props() {
    return Props.create(PFSCluster.class, PFSCluster::new);
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();
    this.cluster.subscribe(this.self(),
        ClusterEvent.initialStateAsEvents(), // replays current state as events
        ClusterEvent.MemberEvent.class, // entails Member[Exited|Joined|Left|Removed|Up|WeaklyUp]
        ClusterEvent.UnreachableMember.class);
  }

  @Override
  public void postStop() throws Exception {
    this.cluster.unsubscribe(this.self());
    super.postStop();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(ClusterEvent.MemberUp.class, m -> { this.log.info("Member up: {}", m.member()); })
        .match(ClusterEvent.MemberRemoved.class,
            m -> { this.log.info("Member removed: {}", m.member()); })
        .match(ClusterEvent.UnreachableMember.class,
            m -> { this.log.info("Member unreachable: {}", m.member()); })
        .match(ClusterEvent.MemberEvent.class,
            m
            -> {
                // catch all
            })
        .build();
  }
}
