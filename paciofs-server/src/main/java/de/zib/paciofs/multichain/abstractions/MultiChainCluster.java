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
import de.zib.paciofs.multichain.MultiChainUtil;
import de.zib.paciofs.multichain.actors.MultiChainActor;
import de.zib.paciofs.multichain.internal.MultiChainCommand;
import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

public class MultiChainCluster implements MultiChainActor.RawTransactionConsumer {
  private static final Logger LOG = LoggerFactory.getLogger(MultiChainCluster.class);

  private static final BigDecimal CLUSTER_OP_RETURN_FEE = new BigDecimal(1);

  private final MultiChainUtil clientUtil;

  private final Map<String, Node> nodes;

  private Node self;

  /**
   * Create a cluster abstraction for this MultiChain.
   * @param client the MultiChain client to use
   */
  public MultiChainCluster(MultiChainRpcClient client) {
    this.clientUtil = new MultiChainUtil(client, CLUSTER_OP_RETURN_FEE, LOG);
    this.nodes = new ConcurrentHashMap<>();
  }

  /**
   * Prepares and sends a transaction that adds a node. After the transaction has been accepted,
   * considers the node to be added.
   * @param node the node to add
   */
  public Node addNode(Node node) {
    Node added = this.nodes.merge(node.getAddress(), node, (old, toAdd) -> {
      if ("".equals(old.getCreationTxId()) && !"".equals(toAdd.getCreationTxId())) {
        // if the new node has a creation transaction ID and the old one does not, then update
        return toAdd;
      }

      // nodes are either the same, or the new one does not have a creation transaction ID
      return old;
    });

    if (added == node) {
      LOG.info("Node {} was added to cluster", TextFormat.shortDebugString(node));

      // send the node to the chain as we have not seen it before
      if ("".equals(node.getCreationTxId())) {
        final String txId =
            this.clientUtil.sendRawTransaction(MultiChainCommand.MCC_NODE_ADD, node.toByteArray());
        added = Node.newBuilder(added).setCreationTxId(txId).build();
      }
    } else {
      LOG.warn("Node {} is already present in cluster", TextFormat.shortDebugString(node));
    }

    return added;
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

  public boolean ready() {
    // we are ready if our node has a creation transaction ID
    return !"".equals(this.self.getCreationTxId());
  }

  @Override
  public void consumeRawTransaction(final BitcoindRpcClient.RawTransaction rawTransaction) {
    LOG.trace("Received raw tx: {}", rawTransaction.txId());

    // add ourselves to the cluster upon receiving the first transaction
    if (this.self == null) {
      synchronized (this) {
        if (this.self == null) {
          final InetAddress localhost;
          try {
            localhost = InetAddress.getLocalHost();
          } catch (UnknownHostException e) {
            throw new RuntimeException("Could not get localhost", e);
          }
          final Node node = Node.newBuilder().setAddress(localhost.getHostAddress()).build();
          LOG.info("Adding self ({}) to cluster", TextFormat.shortDebugString(node));

          // this will send a transaction which we will receive later on
          this.addNode(node);
          this.self = node;
        }
      }
    }

    this.clientUtil.processRawTransaction(rawTransaction, (command, data) -> {
      try {
        switch (command) {
          case MCC_NODE_ADD: {
            final Node node = Node.newBuilder(Node.parseFrom(data))
                                  .setCreationTxId(rawTransaction.txId())
                                  .build();
            this.addNode(node);

            // once we have added ourselves to the cluster, we are ready to operate
            if (node.getAddress().equals(this.self.getAddress())) {
              this.self = node;
              LOG.info("Self ({}) added to cluster", TextFormat.shortDebugString(this.self));
            }

            break;
          }
          case MCC_NODE_REMOVE: {
            final Node node = Node.parseFrom(data);
            this.removeNode(node);
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
  public void unconsumeRawTransaction(BitcoindRpcClient.RawTransaction rawTransaction) {
    LOG.trace("Received raw tx for removal: {}", rawTransaction.txId());
  }
}
