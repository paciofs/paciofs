/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;

public abstract class AbstractMultiChainBroadcastActor extends AbstractActor {
  protected static class MultiChainBroadcast {
    protected final boolean broadcast;

    protected MultiChainBroadcast(boolean broadcast) {
      this.broadcast = broadcast;
    }
  }

  private final ActorRef mediator;

  private final String topic;

  protected AbstractMultiChainBroadcastActor(String topic) {
    this.mediator = DistributedPubSub.get(this.context().system()).mediator();
    this.topic = topic;
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();
    this.mediator.tell(
        new DistributedPubSubMediator.Subscribe(this.topic, this.self()), this.self());
  }

  @Override
  public void postStop() throws Exception {
    this.mediator.tell(
        new DistributedPubSubMediator.Unsubscribe(this.topic, this.self()), this.self());
    super.postStop();
  }

  @Override
  public Receive createReceive() {
    final ReceiveBuilder builder = this.receiveBuilder();

    builder.match(DistributedPubSubMediator.SubscribeAck.class,
        AbstractMultiChainBroadcastActor.this::subscribeAck);
    builder.match(DistributedPubSubMediator.UnsubscribeAck.class,
        AbstractMultiChainBroadcastActor.this::unsubscribeAck);

    return builder.build();
  }

  protected void broadcast(Object message) {
    this.mediator.tell(new DistributedPubSubMediator.Publish(this.topic, message), this.self());
  }

  protected void subscribeAck(DistributedPubSubMediator.SubscribeAck ack) {}

  protected void unsubscribeAck(DistributedPubSubMediator.UnsubscribeAck ack) {}
}
