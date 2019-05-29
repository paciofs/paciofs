/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.actors;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.pf.ReceiveBuilder;
import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import java.time.Duration;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

public class MultiChainActor extends AbstractActorWithTimers {
  public interface RawTransactionConsumer {
    void consumeRawTransaction(BitcoindRpcClient.RawTransaction rawTransaction);

    void unconsumeRawTransaction(BitcoindRpcClient.RawTransaction rawTransaction);
  }

  private static final class MultiChainEndpoint {
    final String endpoint;

    MultiChainEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }
  }

  private static final class MultiChainQuery {
    MultiChainQuery() {}
  }

  private static final int QUERY_INTERVAL = 3000;

  private static final String TOPIC_MULTICHAIN_ENDPOINTS = "multichain-endpoints";

  private static final Logger LOG = LoggerFactory.getLogger(MultiChainActor.class);

  // used for distributed publish/subscribe
  private final ActorRef mediator;

  // keep track of the most recent block in the best chain
  private BitcoindRpcClient.Block multiChainBestBlock;

  // our primary MultiChain instance that we interact with
  private final MultiChainRpcClient multiChainClient;

  // the first address that the MultiChain client listens on
  private String multiChainEndpoint;

  // key for the timer we use to schedule querying of the chain
  private final Object multiChainQueryTimerKey;

  // array of recipients of new raw transactions
  private final RawTransactionConsumer[] rawTransactionConsumers;

  /**
   * Construct a MultiChain actor, which listens for other actors and connects them to the local
   * MultiChain instance. It periodically queries MultiChain for new blocks.
   * @param multiChainClient the MultiChain client to use
   * @param consumers the list of consumers to notify on new blocks
   */
  public MultiChainActor(
      MultiChainRpcClient multiChainClient, RawTransactionConsumer... consumers) {
    this.mediator = DistributedPubSub.get(this.context().system()).mediator();
    this.multiChainClient = multiChainClient;
    this.multiChainQueryTimerKey = new Object();
    this.rawTransactionConsumers = consumers;

    // best block at initialization is the genesis block
    this.multiChainBestBlock = this.multiChainClient.getBlock(0);
  }

  public static Props props(MultiChainRpcClient client, RawTransactionConsumer... consumers) {
    return Props.create(MultiChainActor.class, () -> new MultiChainActor(client, consumers));
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();

    // subscribe to the topic that receives MultiChain endpoints of other actors
    this.mediator.tell(
        new DistributedPubSubMediator.Subscribe(TOPIC_MULTICHAIN_ENDPOINTS, this.self()),
        this.self());

    // kick off constant querying of the blockchain
    this.timers().startSingleTimer(
        this.multiChainQueryTimerKey, new MultiChainQuery(), Duration.ZERO);
  }

  @Override
  public void postStop() throws Exception {
    this.mediator.tell(
        new DistributedPubSubMediator.Unsubscribe(TOPIC_MULTICHAIN_ENDPOINTS, this.self()),
        this.self());
    super.postStop();
  }

  @Override
  public Receive createReceive() {
    final ReceiveBuilder builder = this.receiveBuilder();

    // once this actor is up and running, it receives the subscription notification
    builder.match(DistributedPubSubMediator.SubscribeAck.class, MultiChainActor.this::subscribeAck);
    builder.match(
        DistributedPubSubMediator.UnsubscribeAck.class, MultiChainActor.this::unsubscribeAck);

    // nodes broadcast the first address their MultiChain instance listens on
    builder.match(MultiChainEndpoint.class, MultiChainActor.this::multiChainEndpoint);

    // query the blockchain for new blocks and transactions
    builder.match(MultiChainQuery.class, MultiChainActor.this::multiChainQuery);

    return builder.build();
  }

  private void subscribeAck(DistributedPubSubMediator.SubscribeAck ack) {
    switch (ack.subscribe().topic()) {
      // broadcast the first address the MultiChain client listens on
      case TOPIC_MULTICHAIN_ENDPOINTS:
        // obtain the networking information of the client
        final BitcoindRpcClient.NetworkInfo networkInfo = this.multiChainClient.getNetworkInfo();
        final List<BitcoindRpcClient.LocalAddress> localAddresses = networkInfo.localAddresses();
        if (localAddresses.size() == 0) {
          throw new RuntimeException("MultiChain does not listen on any local addresses");
        } else {
          // remember our address
          this.multiChainEndpoint =
              localAddresses.get(0).address() + ":" + localAddresses.get(0).port();
          if (localAddresses.size() > 1) {
            LOG.warn("MultiChain has {} local addresses, broadcasting only: {}",
                localAddresses.size(), this.multiChainEndpoint);
          }

          // broadcast the address, so other actors can tell their MultiChain clients about it
          this.mediator.tell(new DistributedPubSubMediator.Publish(TOPIC_MULTICHAIN_ENDPOINTS,
                                 new MultiChainEndpoint(this.multiChainEndpoint)),
              this.self());
        }
        break;
      default:
        throw new IllegalArgumentException(ack.subscribe().topic());
    }

    LOG.debug("Subscribed to topic {}", ack.subscribe().topic());
  }

  private void unsubscribeAck(DistributedPubSubMediator.UnsubscribeAck ack) {
    LOG.debug("Unsubscribed from topic {}", ack.unsubscribe().topic());
  }

  private void multiChainEndpoint(MultiChainEndpoint endpoint) {
    // we receive our own broadcast as well, so guard against adding ourselves
    if (!endpoint.endpoint.equals(this.multiChainEndpoint)) {
      // tell our MultiChain client about the other actor's client
      LOG.trace("Adding MultiChain endpoint {}", endpoint.endpoint);
      this.multiChainClient.addNode(endpoint.endpoint, "add");
    }
  }

  private void multiChainQuery(MultiChainQuery query) {
    // the chain should be queried for new transactions
    LOG.trace("Querying chain");

    // get the most recent block of the current best chain
    BitcoindRpcClient.Block bestBlock =
        this.multiChainClient.getBlock(this.multiChainClient.getBestBlockHash());

    // sanity check
    if (bestBlock.height() < this.multiChainBestBlock.height()) {
      // block height should never decrease, fork or not
      LOG.error("Best chain contains fewer blocks than before ({} < {})", bestBlock.height(),
          this.multiChainBestBlock.height());
    } else if (!bestBlock.hash().equals(this.multiChainBestBlock.hash())) {
      // the new best block is different from the one we processed last time
      // 1) the new best block could be a successor to the last best block (no fork)
      // 2) the new best block could be a sibling of the last best block (fork)
      LOG.trace("Updating from block {} to {} (height {} to {})", this.multiChainBestBlock.hash(),
          bestBlock.hash(), this.multiChainBestBlock.height(), bestBlock.height());

      // build the branch of the best chain down to the last block we processed
      final Deque<BitcoindRpcClient.Block> branch = new LinkedList<>();

      // the new best block must be processed anyway, successor or sibling
      branch.addFirst(bestBlock);

      // add all new blocks until they would be direct successors of the last best block
      while (branch.getFirst().height() > this.multiChainBestBlock.height() + 1) {
        branch.addFirst(branch.getFirst().previous());
      }

      // it the best chain's first block's predecessor's hash is not the best block we have last
      // processed, then a fork happened
      while (!branch.getFirst().previousHash().equals(this.multiChainBestBlock.hash())) {
        // remove previously processed blocks
        LOG.trace("Unprocessing block {}", this.multiChainBestBlock.hash());

        for (String tx : this.multiChainBestBlock.tx()) {
          final BitcoindRpcClient.RawTransaction rawTransaction =
              this.multiChainClient.getRawTransaction(tx);
          for (RawTransactionConsumer consumer : this.rawTransactionConsumers) {
            consumer.unconsumeRawTransaction(rawTransaction);
          }
        }

        // keep adding to the best chain while going back
        branch.addFirst(branch.getFirst().previous());
        this.multiChainBestBlock = this.multiChainBestBlock.previous();
      }

      // now process all new blocks
      while (branch.size() > 0) {
        this.multiChainBestBlock = branch.pollFirst();
        LOG.trace("Processing block {}", this.multiChainBestBlock.hash());

        for (String tx : this.multiChainBestBlock.tx()) {
          final BitcoindRpcClient.RawTransaction rawTransaction =
              this.multiChainClient.getRawTransaction(tx);
          for (RawTransactionConsumer consumer : this.rawTransactionConsumers) {
            consumer.consumeRawTransaction(rawTransaction);
          }
        }
      }
    }

    // get the best block again to see if it has changed in the meantime
    bestBlock = this.multiChainClient.getBlock(this.multiChainClient.getBestBlockHash());

    // schedule the next invocation immediately if the best block has changed, otherwise wait a bit
    this.timers().startSingleTimer(this.multiChainQueryTimerKey, query,
        bestBlock.hash().equals(this.multiChainBestBlock.hash()) ? Duration.ofMillis(QUERY_INTERVAL)
                                                                 : Duration.ZERO);
  }
}
