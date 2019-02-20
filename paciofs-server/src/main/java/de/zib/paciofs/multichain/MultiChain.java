/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.Member;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import de.zib.paciofs.cluster.AbstractClusterDomainEventListener;
import de.zib.paciofs.logging.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import wf.bitcoin.javabitcoindrpcclient.GenericRpcException;

public class MultiChain extends AbstractClusterDomainEventListener {
  public interface MultiChainCommand {}

  private static class SubscribeToStreamInternal implements MultiChainCommand {
    public final String creationTxId;

    private SubscribeToStreamInternal(String creationTxId) {
      this.creationTxId = creationTxId;
    }
  }

  public static class SubscribeToStream extends SubscribeToStreamInternal {
    public SubscribeToStream(String creationTxId) {
      super(creationTxId);
    }
  }

  private static final String STREAM_TOPIC = "stream";

  private static final Logger LOG = LoggerFactory.getLogger(MultiChain.class);

  private final ActorRef mediator;

  private final MultiChainDaemonRpcClient multiChainClient;

  private MultiChain(MultiChainDaemonRpcClient multiChainClient) {
    // for talking to all other MultiChain actors in the cluster
    this.mediator = DistributedPubSub.get(this.context().system()).mediator();

    // subscribe to events related to streams
    this.mediator.tell(
        new DistributedPubSubMediator.Subscribe(STREAM_TOPIC, this.self()), this.self());

    // for talking to MultiChain itself
    this.multiChainClient = multiChainClient;
  }

  public static Props props(MultiChainDaemonRpcClient multiChainClient) {
    return Props.create(MultiChain.class, () -> new MultiChain(multiChainClient));
  }

  @Override
  public Receive createReceive() {
    return super.createReceive().orElse(
        this.receiveBuilder()
            .match(DistributedPubSubMediator.SubscribeAck.class,
                e -> LOG.info("Subscribed to topic {}", e.subscribe().topic()))
            .match(SubscribeToStream.class,
                e
                -> this.mediator.tell(new DistributedPubSubMediator.Publish(STREAM_TOPIC,
                                          new SubscribeToStreamInternal(e.creationTxId)),
                    this.self()))
            .match(SubscribeToStreamInternal.class,
                e -> this.multiChainClient.subscribe(e.creationTxId))
            .build());
  }

  @Override
  protected void memberDowned(Member member) {
    super.memberDowned(member);
    this.removeNode(member);
  }

  @Override
  protected void memberExited(Member member) {
    super.memberExited(member);
    this.removeNode(member);
  }

  @Override
  protected void memberLeft(Member member) {
    super.memberLeft(member);
    this.removeNode(member);
  }

  @Override
  protected void memberRemoved(Member member) {
    super.memberRemoved(member);
    this.removeNode(member);
  }

  @Override
  protected void memberUp(Member member) {
    super.memberUp(member);
    this.addNode(member);
  }

  @Override
  protected void memberWeaklyUp(Member member) {
    super.memberWeaklyUp(member);
    this.addNode(member);
  }

  private void addNode(Member member) {
    final Option<String> host = member.address().host();
    if (host.isEmpty()) {
      LOG.warn("Cannot add node with empty host: {}", member);
    } else {
      try {
        LOG.trace("Adding node: {}", host.get());
        this.multiChainClient.addNode(host.get(), "add");
        LOG.trace("Added node: {}", host.get());
      } catch (GenericRpcException e) {
        LOG.warn("Adding node failed: {}", e.getMessage());
        LOG.warn(Markers.EXCEPTION, "Adding node failed", e);
      }
    }

    // have MultiChain measure latency and backlog
    this.multiChainClient.ping();
  }

  private void removeNode(Member member) {
    final Option<String> host = member.address().host();
    if (host.isEmpty()) {
      LOG.warn("Cannot remove with empty host: {}", member);
    } else {
      try {
        LOG.trace("Removing node: {}", host.get());
        this.multiChainClient.addNode(host.get(), "remove");
        LOG.trace("Removed node: {}", host.get());
      } catch (GenericRpcException e) {
        LOG.warn("Removing node failed: {}", e.getMessage());
        LOG.warn(Markers.EXCEPTION, "Removing node failed", e);
      }
    }

    // have MultiChain measure latency and backlog
    this.multiChainClient.ping();
  }
}
