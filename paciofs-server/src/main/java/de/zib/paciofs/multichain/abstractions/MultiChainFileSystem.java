/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.abstractions;

import de.zib.paciofs.grpc.messages.Volume;
import de.zib.paciofs.multichain.actors.MultiChainActor;
import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

public class MultiChainFileSystem implements MultiChainActor.RawTransactionConsumer {
  private static final Logger LOG = LoggerFactory.getLogger(MultiChainFileSystem.class);

  private static final BigDecimal FILE_SYSTEM_OP_RETURN_FEE = new BigDecimal(1);

  private static final String VOLUME_CREATE = "PACIVOCR";
  private static final String VOLUME_DELETE = "PACIVODL";

  private final MultiChainRpcClient client;

  private final String changeAddress;

  private final MultiChainCluster cluster;

  public MultiChainFileSystem(MultiChainRpcClient client, MultiChainCluster cluster) {
    this.client = client;
    this.changeAddress = this.client.getNewAddress();
    this.cluster = cluster;
  }

  public void createVolume(Volume volume) {}

  @Override
  public void consumeRawTransaction(BitcoindRpcClient.RawTransaction rawTransaction) {
    LOG.trace("Received raw tx: {}", rawTransaction.txId());
  }

  @Override
  public void unconsumeRawTransaction(BitcoindRpcClient.RawTransaction rawTransaction) {
    LOG.trace("Received raw tx for removal: {}", rawTransaction.txId());
  }
}
