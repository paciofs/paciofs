/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCException;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;
import wf.bitcoin.krotjson.HexCoder;

public class MultiChainUtil {
  private static final String OP_RETURN = "OP_RETURN";
  private static final int OP_RETURN_MAX_BYTES = 80;

  private static final Charset UTF8 = Charset.forName("UTF-8");

  private MultiChainUtil() {}

  public static String decodeRawTransactionData(byte[] data) {
    return new String(data, UTF8);
  }

  /**
   * Encodes string data to be embedded in a transaction output.
   * @param data data to encode
   * @return the encoded data
   */
  public static byte[] encodeRawTransactionData(String data) {
    final byte[] bytes = data.getBytes(UTF8);
    if (bytes.length > OP_RETURN_MAX_BYTES) {
      throw new IllegalArgumentException("UTF-8 encoding of '" + data + "' is larger than "
          + OP_RETURN_MAX_BYTES + " bytes: " + bytes.length);
    }

    return bytes;
  }

  /**
   * If the transaction output is an OP_RETURN, returns the decoded data.
   * @param out transaction output to check
   * @return decoded data for OP_RETURN, null otherwise
   */
  public static byte[] getRawTransactionData(BitcoindRpcClient.RawTransaction.Out out) {
    final String asm = out.scriptPubKey().asm();
    if (!asm.startsWith(OP_RETURN)) {
      return null;
    }

    return HexCoder.decode(asm.substring(OP_RETURN.length()).trim());
  }

  public static String sendRawTransaction(MultiChainRpcClient client, Logger traceLog,
      int utxoMinConfirmations, BigDecimal amount, String address, byte[] data) {
    // get this wallet's UTXOs with a certain number of confirmations
    final List<BitcoindRpcClient.Unspent> utxos = client.listUnspent(utxoMinConfirmations);

    // find a fitting UTXO and create input and output
    List<BitcoindRpcClient.TxInput> input = null;
    List<BitcoindRpcClient.TxOutput> output = null;
    for (BitcoindRpcClient.Unspent utxo : utxos) {
      if (utxo.amount().compareTo(amount) >= 0 && utxo.spendable()) {
        // use this UTXO as spendable input
        input = Collections.singletonList(
            new BitcoindRpcClient.BasicTxInput(utxo.txid(), utxo.vout(), utxo.scriptPubKey()));

        // send to our change address
        output = Collections.singletonList(
            new BitcoindRpcClient.BasicTxOutput(address, utxo.amount().subtract(amount), data));

        break;
      }
    }

    // wallet did not contain any spendable unspent transaction output
    if (input == null) {
      throw new BitcoinRPCException("No spendable UTXO with amount >= " + amount);
    }

    // build the raw transaction
    final String rawTransactionHex = client.createRawTransaction(input, output);
    if (traceLog != null && traceLog.isTraceEnabled()) {
      traceLog.trace("Raw transaction: {}", client.decodeRawTransaction(rawTransactionHex));
    }

    // sign the raw transaction
    final String signedRawTransactionHex =
        client.signRawTransaction(rawTransactionHex, input, null);

    // finally submit the transaction
    final String transactionId = client.sendRawTransaction(signedRawTransactionHex);
    if (traceLog != null && traceLog.isTraceEnabled()) {
      traceLog.trace("Sent raw transaction: {}", client.getRawTransaction(transactionId));
    }

    return transactionId;
  }
}
