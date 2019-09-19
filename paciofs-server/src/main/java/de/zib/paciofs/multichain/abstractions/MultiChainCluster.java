/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.abstractions;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import de.zib.paciofs.grpc.messages.Node;
import de.zib.paciofs.multichain.MultiChainData;
import de.zib.paciofs.multichain.MultiChainUtil;
import de.zib.paciofs.multichain.actors.MultiChainActor;
import de.zib.paciofs.multichain.internal.MultiChainCommand;
import de.zib.paciofs.multichain.rpc.MultiChainClient;
import de.zib.paciofs.multichain.rpc.types.RawTransaction;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiChainCluster implements MultiChainActor.RawTransactionConsumer {
  private static final Logger LOG = LoggerFactory.getLogger(MultiChainCluster.class);

  // the equivalent of one satoshi
  private static final BigDecimal CLUSTER_OP_RETURN_FEE = new BigDecimal(1.0 / 100_000_000.0);

  private final MultiChainUtil clientUtil;

  private final Map<String, Node> nodes;

  private final InetAddress localhost;

  /**
   * Create a cluster abstraction for this MultiChain.
   * @param client the MultiChain client to use
   */
  public MultiChainCluster(MultiChainClient client) {
    this.clientUtil = new MultiChainUtil(client, CLUSTER_OP_RETURN_FEE, LOG);
    this.nodes = new ConcurrentHashMap<>();

    try {
      this.localhost = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      throw new RuntimeException("Could not get localhost", e);
    }
  }

  /**
   * Prepares and sends a transaction that adds a node. After the transaction has been accepted,
   * considers the node to be added.
   * @param node the node to add
   */
  public Node addNode(Node node) {
    if (this.nodes.containsKey(node.getAddress())) {
      throw new IllegalArgumentException(
          "Node " + TextFormat.shortDebugString(node) + " is already present in cluster");
    }

    final MultiChainData data = new MultiChainData();
    data.writeByteArray(node.toByteArray());

    final String txId = this.clientUtil.sendRawTransaction(MultiChainCommand.MCC_NODE_ADD, data);
    node = Node.newBuilder(node).setCreationTxId(txId).build();
    this.addNodeFromTransaction(node);
    return node;
  }

  private void addNodeFromTransaction(Node node) {
    if (this.nodes.containsKey(node.getAddress())) {
      LOG.debug("Node {} is already present in cluster", TextFormat.shortDebugString(node));
      return;
    }

    this.nodes.put(node.getAddress(), node);
    LOG.debug("Node {} was added to cluster", TextFormat.shortDebugString(node));
  }

  /**
   * Prepares and sends a transaction that removes a node. After the transaction has been accepted,
   * considers the node to be removed.
   * @param node the node to remove
   */
  public Node removeNode(Node node) {
    // TODO implement
    throw new UnsupportedOperationException();
  }

  private void removeNodeFromTransaction(Node node) {
    // TODO implement
    throw new UnsupportedOperationException();
  }

  public boolean ready() {
    return this.clusterContainsSelf();
  }

  private boolean clusterContainsSelf() {
    return this.nodes.containsKey(this.localhost.getHostAddress());
  }

  @Override
  public void consumeRawTransaction(final RawTransaction rawTransaction) {
    LOG.trace("Received raw tx: {}", rawTransaction.id());

    this.clientUtil.processRawTransaction(rawTransaction, (command, data) -> {
      try {
        switch (command) {
          case MCC_NODE_ADD: {
            final Node node = Node.newBuilder(Node.parseFrom(data.readByteArray()))
                                  .setCreationTxId(rawTransaction.id())
                                  .build();
            this.addNodeFromTransaction(node);
            break;
          }
          case MCC_NODE_REMOVE: {
            final Node node = Node.parseFrom(data.readByteArray());
            this.removeNodeFromTransaction(node);
            break;
          }
          default:
            // not for us, ignore
            break;
        }
      } catch (InvalidProtocolBufferException e) {
        // should not happen because at this point we know what data to expect
        MultiChainCluster.LOG.error("Error parsing data", e);
      }
    });
  }

  @Override
  public void doneProcessingRawTransactions() {
    if (this.clusterContainsSelf()) {
      return;
    }

    final Node self = Node.newBuilder().setAddress(this.localhost.getHostAddress()).build();
    LOG.debug("Adding self ({}) to cluster", TextFormat.shortDebugString(self));

    // this will send a transaction which we will receive later on
    this.addNode(self);
  }

  @Override
  public void unconsumeRawTransaction(RawTransaction rawTransaction) {
    LOG.trace("Received raw tx for removal: {}", rawTransaction.id());
  }
}
