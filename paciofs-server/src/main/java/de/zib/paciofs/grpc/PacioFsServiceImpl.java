/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.grpc;

import de.zib.paciofs.grpc.messages.Ping;
import de.zib.paciofs.grpc.messages.Volume;
import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCException;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

public class PacioFsServiceImpl implements PacioFsService {
  private static final Logger LOG = LoggerFactory.getLogger(PacioFsServiceImpl.class);

  // fee to pay for OP_RETURN transactions
  private static final BigDecimal OP_RETURN_TRANSACTION_FEE = new BigDecimal(0);

  private static final Charset CHARSET = Charset.forName("UTF-8");

  private static final byte[] PACIOFS = "PACI".getBytes(CHARSET);
  private static final byte[] VOLUME_CREATE = "VOCR".getBytes(CHARSET);

  private final MultiChainRpcClient multiChainClient;

  private final String changeAddress;

  public PacioFsServiceImpl(MultiChainRpcClient multiChainClient) {
    this.multiChainClient = multiChainClient;
    this.changeAddress = this.multiChainClient.getNewAddress();
  }

  @Override
  public CompletionStage<CreateVolumeResponse> createVolume(CreateVolumeRequest in) {
    PacioFsGrpcUtil.traceMessages(LOG, "createVolume({})", in);

    // obtain all unspent transaction outputs (UTXOs) for this wallet, meaning we can spend them
    final List<BitcoindRpcClient.Unspent> unspents = this.multiChainClient.listUnspent(0);

    final String volumeName = in.getVolume().getName();

    // find a fitting UTXO and create input and output
    List<BitcoindRpcClient.TxInput> input = null;
    List<BitcoindRpcClient.TxOutput> output = null;
    for (BitcoindRpcClient.Unspent unspent : unspents) {
      if (unspent.amount().compareTo(OP_RETURN_TRANSACTION_FEE) >= 0 && unspent.spendable()) {
        // use this UTXO as spendable input
        input = Collections.singletonList(new BitcoindRpcClient.BasicTxInput(
            unspent.txid(), unspent.vout(), unspent.scriptPubKey()));

        // send to our change address
        output = Collections.singletonList(new BitcoindRpcClient.BasicTxOutput(this.changeAddress,
            unspent.amount().subtract(OP_RETURN_TRANSACTION_FEE), createVolumeData(volumeName)));

        break;
      }
    }

    // wallet did not contain any spendable unspent transaction output
    if (input == null) {
      throw PacioFsGrpcUtil.toGrpcServiceException(
          new BitcoinRPCException("No spendable UTXO with amount >= " + OP_RETURN_TRANSACTION_FEE));
    }

    // build the raw transaction
    final String rawTransactionHex = this.multiChainClient.createRawTransaction(input, output);
    if (LOG.isTraceEnabled()) {
      LOG.trace("Creating volume {} with raw transaction: {}", volumeName,
          this.multiChainClient.decodeRawTransaction(rawTransactionHex));
    }

    // sign the raw transaction
    final String signedRawTransactionHex =
        this.multiChainClient.signRawTransaction(rawTransactionHex, input, null);

    // finally submit the transaction
    final String creationTransactionId =
        this.multiChainClient.sendRawTransaction(signedRawTransactionHex);
    if (LOG.isTraceEnabled()) {
      LOG.trace("Created volume {} with raw transaction: {}", volumeName,
          this.multiChainClient.getRawTransaction(creationTransactionId));
    }

    final Volume volume = Volume.newBuilder().build();
    final CreateVolumeResponse out = CreateVolumeResponse.newBuilder().setVolume(volume).build();

    PacioFsGrpcUtil.traceMessages(LOG, "createVolume({}): {}", in, out);
    return CompletableFuture.completedFuture(out);
  }

  @Override
  public CompletionStage<PingResponse> ping(PingRequest in) {
    PacioFsGrpcUtil.traceMessages(LOG, "ping({})", in);

    final Ping ping = Ping.newBuilder().build();
    final PingResponse out = PingResponse.newBuilder().setPing(ping).build();

    PacioFsGrpcUtil.traceMessages(LOG, "ping({}): {}", in, out);
    return CompletableFuture.completedFuture(out);
  }

  private static byte[] createVolumeData(String volumeName) {
    return concatArrays(PACIOFS, VOLUME_CREATE, volumeName.getBytes(CHARSET));
  }

  private static byte[] concatArrays(byte[]... arrays) {
    int length = 0;
    for (byte[] a : arrays) {
      length += a.length;
    }

    final byte[] result = new byte[length];
    int currentLength = 0;
    for (byte[] a : arrays) {
      System.arraycopy(a, 0, result, currentLength, a.length);
      currentLength += a.length;
    }

    return result;
  }
}
