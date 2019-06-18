/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import de.zib.paciofs.multichain.internal.MultiChainCommand;
import de.zib.paciofs.multichain.internal.MultiChainRawTransactionDataHeader;
import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCException;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;
import wf.bitcoin.krotjson.HexCoder;

public class MultiChainUtil {
  // magic number in every raw transaction's data header
  // 'P' << 24 | 'A' << 16 | 'C' << 8 | 'I';
  private static final int HEADER_MAGIC = 1346454345;

  private static final int UTXO_MIN_CONFIRMATIONS = 0;

  private final MultiChainRpcClient client;

  private final BigDecimal amount;

  private final String changeAddress;

  private final Logger log;

  /**
   * Constructs a utility around a MultiChain client, providing some added functionality.
   * @param client the MultiChain client to wrap
   * @param amount the amount to send in each transaction
   * @param log the logger to use
   */
  public MultiChainUtil(MultiChainRpcClient client, BigDecimal amount, Logger log) {
    this.client = client;
    this.changeAddress = this.client.getNewAddress();
    this.amount = amount;
    this.log = log;
  }

  /**
   * Takes a raw transaction and iterates over all outputs. If an output is found that contains
   * encoded data, extracts the corresponding command and passes it to the consumer, along with the
   * data.
   * @param rawTransaction the raw transaction to iterate over
   * @param consumer callback taking a command along with data
   */
  public void processRawTransaction(BitcoindRpcClient.RawTransaction rawTransaction,
      BiConsumer<MultiChainCommand, MultiChainData> consumer) {
    // TODO build fixed-size FIFO cache of raw transactions
    for (BitcoindRpcClient.RawTransaction.Out out : rawTransaction.vOut()) {
      if (out.scriptPubKey() != null && "nulldata".equals(out.scriptPubKey().type())) {
        final CodedInputStream stream = CodedInputStream.newInstance(
            HexCoder.decode(out.scriptPubKey().asm().substring("OP_RETURN ".length())));
        try {
          // limit to header size to avoid reading past the end
          final int headerLength = stream.readUInt32();
          final int limit = stream.pushLimit(headerLength);
          final MultiChainRawTransactionDataHeader header =
              MultiChainRawTransactionDataHeader.parseFrom(stream);
          stream.popLimit(limit);

          if (header.getMagic() == HEADER_MAGIC) {
            final int dataLength = stream.readUInt32();
            final MultiChainData data = new MultiChainData(stream.readRawBytes(dataLength));
            consumer.accept(header.getCommand(), data);
          }
        } catch (InvalidProtocolBufferException e) {
          // invalid header, no raw transaction we can process
        } catch (IOException e) {
          throw new RuntimeException("Error reading raw transaction data", e);
        }
      }
    }
  }

  /**
   * Builds, signs and sends a raw transaction.
   * @param command the command to prepend to the data
   * @param data the actual data to add to OP_RETURN
   * @return the transaction id
   */
  public String sendRawTransaction(MultiChainCommand command, MultiChainData data) {
    // get this wallet's UTXOs with a certain number of confirmations
    final List<BitcoindRpcClient.Unspent> utxos = this.client.listUnspent(UTXO_MIN_CONFIRMATIONS);

    // find a fitting UTXO and create input and output
    List<BitcoindRpcClient.TxInput> input = null;
    List<BitcoindRpcClient.TxOutput> output = null;
    for (BitcoindRpcClient.Unspent utxo : utxos) {
      if (utxo.amount().compareTo(this.amount) >= 0 && utxo.spendable()) {
        // use this UTXO as spendable input
        input = Collections.singletonList(
            new BitcoindRpcClient.BasicTxInput(utxo.txid(), utxo.vout(), utxo.scriptPubKey()));

        // build the header so we know the size
        final MultiChainRawTransactionDataHeader header =
            MultiChainRawTransactionDataHeader.newBuilder()
                .setMagic(HEADER_MAGIC)
                .setCommand(command)
                .build();

        // build the data array
        final byte[] dataArray = data.toByteArray();
        final byte[] out = new byte[CodedOutputStream.computeMessageSizeNoTag(header)
            + CodedOutputStream.computeByteArraySizeNoTag(dataArray)];
        try {
          final CodedOutputStream stream = CodedOutputStream.newInstance(out);

          // both methods prepend lengths as uint32 fields
          stream.writeMessageNoTag(header);
          stream.writeByteArrayNoTag(dataArray);

          stream.flush();
        } catch (IOException e) {
          throw new RuntimeException("Error writing raw transaction data", e);
        }

        // send to our change address
        output = Collections.singletonList(new BitcoindRpcClient.BasicTxOutput(
            this.changeAddress, utxo.amount().subtract(this.amount), out));

        break;
      }
    }

    // wallet did not contain any spendable unspent transaction output
    if (input == null) {
      throw new BitcoinRPCException("No spendable UTXO with amount >= " + this.amount);
    }

    // build the raw transaction
    final String rawTransactionHex = this.client.createRawTransaction(input, output);
    if (this.log.isTraceEnabled()) {
      this.log.trace("Raw transaction: {}", this.client.decodeRawTransaction(rawTransactionHex));
    }

    // sign the raw transaction
    final String signedRawTransactionHex =
        this.client.signRawTransaction(rawTransactionHex, input, null);

    // finally submit the transaction
    final String transactionId = this.client.sendRawTransaction(signedRawTransactionHex);
    if (this.log.isTraceEnabled()) {
      this.log.trace("Sent raw transaction: {}", this.client.getRawTransaction(transactionId));
    }

    return transactionId;
  }
}
