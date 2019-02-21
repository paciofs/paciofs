/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.actors;

import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;
import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiChainStreamBroadcastActor extends AbstractMultiChainBroadcastActor {
  public static class SubscribeToStream extends MultiChainBroadcast {
    private final String stream;

    public SubscribeToStream(String stream) {
      super(true);
      this.stream = stream;
    }

    private SubscribeToStream(boolean broadcast, String stream) {
      super(broadcast);
      this.stream = stream;
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(MultiChainStreamBroadcastActor.class);

  private final MultiChainRpcClient client;

  private MultiChainStreamBroadcastActor(MultiChainRpcClient client) {
    super("stream");
    this.client = client;
  }

  public static Props props(MultiChainRpcClient client) {
    return Props.create(
        MultiChainStreamBroadcastActor.class, () -> new MultiChainStreamBroadcastActor(client));
  }

  @Override
  public Receive createReceive() {
    final ReceiveBuilder builder = this.receiveBuilder();

    // TODO subscribe / reindex on start

    builder.match(SubscribeToStream.class, subscribe -> {
      if (subscribe.broadcast) {
        LOG.trace("Broadcasting stream subscription to {}", subscribe.stream);
        MultiChainStreamBroadcastActor.this.broadcast(
            new SubscribeToStream(false, subscribe.stream));
        this.sender().tell(subscribe, this.self());
      } else {
        LOG.trace("Subscribing to stream {}", subscribe.stream);
        MultiChainStreamBroadcastActor.this.client.subscribe(subscribe.stream);
      }
    });

    return super.createReceive().orElse(builder.build());
  }

  @Override
  protected void subscribeAck(DistributedPubSubMediator.SubscribeAck ack) {
    super.subscribeAck(ack);
    LOG.debug("Subscribed to topic {}", ack.subscribe().topic());
  }

  @Override
  protected void unsubscribeAck(DistributedPubSubMediator.UnsubscribeAck ack) {
    super.unsubscribeAck(ack);
    LOG.debug("Unsubscribed from topic {}", ack.unsubscribe().topic());
  }
}
