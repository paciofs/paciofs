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
import de.zib.paciofs.logging.Markers;
import de.zib.paciofs.multichain.internal.MultiChainCommand;
import de.zib.paciofs.multichain.internal.MultiChainRawTransactionDataHeader;
import de.zib.paciofs.multichain.rpc.MultiChainClient;
import de.zib.paciofs.multichain.rpc.types.MultiChainException;
import de.zib.paciofs.multichain.rpc.types.RawTransaction;
import de.zib.paciofs.multichain.rpc.types.TransactionInput;
import de.zib.paciofs.multichain.rpc.types.TransactionInputList;
import de.zib.paciofs.multichain.rpc.types.TransactionOutput;
import de.zib.paciofs.multichain.rpc.types.TransactionOutputList;
import de.zib.paciofs.multichain.rpc.types.UnspentTransactionOutput;
import de.zib.paciofs.multichain.rpc.types.UnspentTransactionOutputList;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Random;
import java.util.function.BiConsumer;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;

public class MultiChainUtil {
  // magic number in every raw transaction's data header
  // 'P' << 24 | 'A' << 16 | 'C' << 8 | 'I';
  private static final int HEADER_MAGIC = 1346454345;

  private static final int UTXO_MIN_CONFIRMATIONS = 0;

  private final MultiChainClient client;

  private final BigDecimal amount;

  private final String changeAddress;

  private final Logger log;

  private final Random random;

  private UnspentTransactionOutputList cachedUtxos;

  /**
   * Constructs a utility around a MultiChain client, providing some added functionality.
   * @param client the MultiChain client to wrap
   * @param amount the amount to send in each transaction
   * @param log the logger to use
   */
  public MultiChainUtil(MultiChainClient client, BigDecimal amount, Logger log) {
    this.client = client;
    this.changeAddress = this.client.getRawChangeAddress();
    this.amount = amount;
    this.log = log;
    this.random = new Random();
    this.cachedUtxos = new UnspentTransactionOutputList();
  }

  /**
   * Takes a raw transaction and iterates over all outputs. If an output is found that contains
   * encoded data, extracts the corresponding command and passes it to the consumer, along with the
   * data.
   * @param rawTransaction the raw transaction to iterate over
   * @param consumer callback taking a command along with data
   */
  public void processRawTransaction(
      RawTransaction rawTransaction, BiConsumer<MultiChainCommand, MultiChainData> consumer) {
    // TODO build fixed-size FIFO cache of raw transactions
    for (RawTransaction.Out out : rawTransaction.vOut()) {
      if (out.scriptPubKey() != null && "nulldata".equals(out.scriptPubKey().type())) {
        final byte[] opReturnData;
        try {
          opReturnData = Hex.decodeHex(out.scriptPubKey().asm().substring("OP_RETURN ".length()));
        } catch (DecoderException e) {
          throw new RuntimeException("Could not decode OP_RETURN data", e);
        }

        final CodedInputStream stream = CodedInputStream.newInstance(opReturnData);
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

  public String sendRawTransaction(MultiChainCommand command, MultiChainData data) {
    String transactionId = null;
    while (transactionId == null) {
      try {
        transactionId = this.doSendRawTransaction(command, data);
      } catch (MultiChainException e) {
        this.log.debug("Sending raw transaction failed ({}), retrying ...", e.getMessage());
        this.log.debug(Markers.EXCEPTION, "Sending raw transaction failed", e);
        this.cachedUtxos = this.client.listUnspent(UTXO_MIN_CONFIRMATIONS);
      }
    }

    return transactionId;
  }

  /**
   * Builds, signs and sends a raw transaction.
   * @param command the command to prepend to the data
   * @param data the actual data to add to OP_RETURN
   * @return the transaction id
   */
  private String doSendRawTransaction(MultiChainCommand command, MultiChainData data) {
    // find fitting UTXOs
    final TransactionInputList inputs = new TransactionInputList();
    BigDecimal currentAmount = BigDecimal.ZERO;
    final int cachedUtxoCount = this.cachedUtxos.size();
    for (int i = 0; i < cachedUtxoCount; ++i) {
      final UnspentTransactionOutput utxo =
          this.cachedUtxos.remove(this.random.nextInt(this.cachedUtxos.size()));
      if (currentAmount.compareTo(this.amount) >= 0) {
        // we have accumulated enough UTXOs
        break;
      }

      if (utxo.spendable()) {
        inputs.add(
            new TransactionInput(utxo.txId(), utxo.vOut(), utxo.scriptPubKey(), utxo.amount()));
        currentAmount = currentAmount.add(utxo.amount());
      }
    }

    if (currentAmount.compareTo(this.amount) < 0) {
      throw new MultiChainException("Not enough spendable UTXOs with sufficient value (got "
          + inputs.size() + " of total value " + currentAmount + ")");
    } else {
      this.log.trace("Got {} UTXOs of value {}", inputs.size(), currentAmount);
    }

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
    final TransactionOutputList outputs = new TransactionOutputList();
    outputs.add(new TransactionOutput(
        this.changeAddress, currentAmount.subtract(this.amount), Hex.encodeHexString(out)));

    // build the raw transaction, sign and send it
    final String transactionId = this.client.createRawTransaction(inputs, outputs, true);
    if (this.log.isTraceEnabled()) {
      this.log.trace("Raw transaction: {}", this.client.getRawTransaction(transactionId));
    }

    return transactionId;
  }
}
