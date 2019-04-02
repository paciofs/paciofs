/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.abstractions;

import de.zib.paciofs.multichain.MultiChainUtil;
import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiChainCluster {
  private static final Logger LOG = LoggerFactory.getLogger(MultiChainCluster.class);

  private static final BigDecimal CLUSTER_OP_RETURN_FEE = new BigDecimal(1);

  private static final String NODE_ADD = "PACINOAD";
  private static final String NODE_REMOVE = "PACINORM";

  private final MultiChainRpcClient client;

  private final String changeAddress;

  private final Set<MultiChainNode> nodes;

  /**
   * Create a cluster abstraction for this MultiChain.
   * @param client the MultiChain client to use
   */
  public MultiChainCluster(MultiChainRpcClient client) {
    this.client = client;
    this.changeAddress = this.client.getNewAddress();
    this.nodes = new ConcurrentSkipListSet<>();

    // TODO init from blockchain
  }

  /**
   * Prepares and sends a transaction that adds a node. After the transaction has been accepted,
   * considers the node to be added.
   * @param address the node's address to add
   */
  public void addNode(String address) {
    final MultiChainNode node = new MultiChainNode(address);
    if (this.nodes.contains(node)) {
      LOG.warn("Node {} is already present in cluster", node);
      return;
    }

    // TODO handle exceptions
    MultiChainUtil.sendTransaction(this.client, LOG, 0, CLUSTER_OP_RETURN_FEE, this.changeAddress,
        MultiChainUtil.encodeRawTransactionData(NODE_ADD + node.getAddress()));

    // TODO check return value and warn
    this.nodes.add(node);
  }

  /**
   * Prepares and sends a transaction that removes a node. After the transaction has been accepted,
   * considers the node to be removed.
   * @param address the node's address to remove
   */
  public void removeNode(String address) {
    final MultiChainNode node = new MultiChainNode(address);
    if (!this.nodes.contains(node)) {
      LOG.warn("Node {} is not present in cluster", node);
      return;
    }

    // TODO handle exceptions
    MultiChainUtil.sendTransaction(this.client, LOG, 0, CLUSTER_OP_RETURN_FEE, this.changeAddress,
        MultiChainUtil.encodeRawTransactionData(NODE_REMOVE + node.getAddress()));

    // TODO check return value and warn
    this.nodes.remove(node);
  }
}
