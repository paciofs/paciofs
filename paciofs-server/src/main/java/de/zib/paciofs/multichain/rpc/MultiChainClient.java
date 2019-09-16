/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc;

import de.zib.paciofs.multichain.rpc.types.Block;
import de.zib.paciofs.multichain.rpc.types.BlockChainInfo;
import de.zib.paciofs.multichain.rpc.types.RawTransaction;
import de.zib.paciofs.multichain.rpc.types.TransactionInputList;
import de.zib.paciofs.multichain.rpc.types.TransactionOutputList;
import de.zib.paciofs.multichain.rpc.types.UnspentTransactionOutputList;

public interface MultiChainClient {
  String createRawTransaction(TransactionInputList inputs, TransactionOutputList outputs);

  String createRawTransaction(
      TransactionInputList inputs, TransactionOutputList outputs, boolean signAndSend);

  RawTransaction decodeRawTransaction(String transactionHex);

  String getBestBlockHash();

  Block getBlock(String blockHash);

  BlockChainInfo getBlockChainInfo();

  String getBlockHash(int height);

  String getNewAddress();

  String getRawChangeAddress();

  RawTransaction getRawTransaction(String id);

  UnspentTransactionOutputList listUnspent(int minimumConfirmations);

  String sendRawTransaction(String transactionHex);

  String signRawTransactionWithWallet(String transactionHex, TransactionInputList inputs);

  void stop();
}
