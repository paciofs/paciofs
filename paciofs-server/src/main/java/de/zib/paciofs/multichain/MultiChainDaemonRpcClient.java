/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

import java.io.Serializable;
import java.math.BigDecimal;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

public interface MultiChainDaemonRpcClient extends BitcoindRpcClient {
  // selected Bitcoin core RPC errors
  // https://github.com/bitcoin/bitcoin/blob/0.17/src/rpc/protocol.h#L31
  int RPC_IN_WARMUP = -28;

  Info getInfo();

  interface Info extends Serializable {
    String version();

    int nodeVersion();

    int protocolVersion();

    String chainName();

    String description();

    String protocol();

    short port();

    long setupBlocks();

    String nodeAddress();

    String burnAddress();

    boolean incomingPaused();

    boolean miningPaused();

    boolean offChainPaused();

    int walletVersion();

    long balance();

    int walletDbVersion();

    boolean reindex();

    int blocks();

    long timeOffset();

    int connections();

    String proxy();

    BigDecimal difficulty();

    boolean testnet();

    long keyPoolOldest();

    int keyPoolSize();

    long payTxFee();

    long relayFee();

    String errors();
  }
}
