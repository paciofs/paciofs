/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.cluster;

import akka.actor.AbstractActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens to cluster events.
 */
public abstract class AbstractClusterDomainEventListener extends AbstractActor {
  private static final Logger LOG =
      LoggerFactory.getLogger(AbstractClusterDomainEventListener.class);

  private final Cluster cluster;

  protected AbstractClusterDomainEventListener() {
    this.cluster = Cluster.get(this.getContext().system());
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();
    this.cluster.subscribe(this.self(), ClusterEvent.initialStateAsEvents(),
        ClusterEvent.DataCenterReachabilityEvent.class, ClusterEvent.MemberEvent.class,
        ClusterEvent.ReachabilityEvent.class);
  }

  @Override
  public void postStop() throws Exception {
    this.cluster.unsubscribe(this.self());
    super.postStop();
  }

  @Override
  public Receive createReceive() {
    return this.receiveBuilder()
        .match(
            ClusterEvent.ReachableDataCenter.class, e -> this.reachableDataCenter(e.dataCenter()))
        .match(ClusterEvent.UnreachableDataCenter.class,
            e -> this.unreachableDataCenter(e.dataCenter()))
        .match(ClusterEvent.MemberDowned.class, e -> this.memberDowned(e.member()))
        .match(ClusterEvent.MemberExited.class, e -> this.memberExited(e.member()))
        .match(ClusterEvent.MemberExited.class, e -> this.memberExited(e.member()))
        .match(ClusterEvent.MemberJoined.class, e -> this.memberJoined(e.member()))
        .match(ClusterEvent.MemberLeft.class, e -> this.memberLeft(e.member()))
        .match(ClusterEvent.MemberRemoved.class, e -> this.memberRemoved(e.member()))
        .match(ClusterEvent.MemberUp.class, e -> this.memberUp(e.member()))
        .match(ClusterEvent.MemberWeaklyUp.class, e -> this.memberWeaklyUp(e.member()))
        .match(ClusterEvent.ReachableMember.class, e -> this.reachableMember(e.member()))
        .match(ClusterEvent.UnreachableMember.class, e -> this.unreachableMember(e.member()))
        .build();
  }

  /* ClusterEvent.DataCenterReachabilityEvent */

  protected void reachableDataCenter(String dataCenter) {
    LOG.trace("Data center reachable: {}", dataCenter);
  }

  protected void unreachableDataCenter(String dataCenter) {
    LOG.trace("Data center unreachable: {}", dataCenter);
  }

  /* ClusterEvent.MemberEvent */

  protected void memberDowned(Member member) {
    LOG.trace("Member downed: {}", member);
  }

  protected void memberExited(Member member) {
    LOG.trace("Member exited: {}", member);
  }

  protected void memberJoined(Member member) {
    LOG.trace("Member joined: {}", member);
  }

  protected void memberLeft(Member member) {
    LOG.trace("Member left: {}", member);
  }

  protected void memberRemoved(Member member) {
    // Member completely removed from the cluster.
    // When previousStatus is MemberStatus.Down the node was removed after being detected as
    // unreachable and downed. When previousStatus is MemberStatus.Exiting the node was removed
    // after graceful leaving and exiting.
    LOG.trace("Member removed: {}", member);
  }

  protected void memberUp(Member member) {
    LOG.trace("Member up: {}", member);
  }

  protected void memberWeaklyUp(Member member) {
    LOG.trace("Member weakly up: {}", member);
  }

  /* ClusterEvent.ReachabilityEvent */

  protected void reachableMember(Member member) {
    LOG.trace("Member reachable: {}", member);
  }

  protected void unreachableMember(Member member) {
    LOG.trace("Member unreachable: {}", member);
  }
}
