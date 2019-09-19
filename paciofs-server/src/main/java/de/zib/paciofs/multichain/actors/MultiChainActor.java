/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.actors;

import akka.actor.AbstractActorWithTimers;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import de.zib.paciofs.multichain.rpc.MultiChainClient;
import de.zib.paciofs.multichain.rpc.types.Block;
import de.zib.paciofs.multichain.rpc.types.RawTransaction;
import de.zib.paciofs.multichain.rpc.types.TransactionInput;
import de.zib.paciofs.multichain.rpc.types.TransactionInputList;
import de.zib.paciofs.multichain.rpc.types.TransactionOutput;
import de.zib.paciofs.multichain.rpc.types.TransactionOutputList;
import de.zib.paciofs.multichain.rpc.types.UnspentTransactionOutput;
import de.zib.paciofs.multichain.rpc.types.UnspentTransactionOutputList;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Deque;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiChainActor extends AbstractActorWithTimers {
  public interface RawTransactionConsumer {
    void consumeRawTransaction(RawTransaction rawTransaction);

    void doneProcessingRawTransactions();

    void unconsumeRawTransaction(RawTransaction rawTransaction);
  }

  private static final class MultiChainEnsureUtxos {
    MultiChainEnsureUtxos() {}
  }

  private static final class MultiChainQuery {
    MultiChainQuery() {}
  }

  private static final int ENSURE_UTXOS_INTERVAL = 500;

  private static final int QUERY_INTERVAL = 3000;

  private static final int MIN_UTXOS = 2000;

  private static final int UTXO_SPLIT_FACTOR = 32;

  private static final BigDecimal MIN_SPLITTABLE_AMOUNT =
      new BigDecimal(UTXO_SPLIT_FACTOR / 100_000_000.0);

  private static final Logger LOG = LoggerFactory.getLogger(MultiChainActor.class);

  // keep track of the most recent block in the best chain
  private Block multiChainBestBlock;

  // our primary MultiChain instance that we interact with
  private final MultiChainClient multiChainClient;

  // key for timer we use to schedule the creation of sufficiently many UTXOs
  private final Object multiChainEnsureUtxosTimerKey;

  // key for the timer we use to schedule querying of the chain
  private final Object multiChainQueryTimerKey;

  // array of recipients of new raw transactions
  private final RawTransactionConsumer[] rawTransactionConsumers;

  private final String[] addresses;

  /**
   * Construct a MultiChain actor, which listens for other actors and connects them to the local
   * MultiChain instance. It periodically queries MultiChain for new blocks.
   * @param multiChainClient the MultiChain client to use
   * @param consumers the list of consumers to notify on new blocks
   */
  public MultiChainActor(MultiChainClient multiChainClient, RawTransactionConsumer... consumers) {
    this.multiChainClient = multiChainClient;
    this.multiChainEnsureUtxosTimerKey = new Object();
    this.multiChainQueryTimerKey = new Object();
    this.rawTransactionConsumers = consumers;

    // we need a new address for each part of a split UTXO
    this.addresses = new String[UTXO_SPLIT_FACTOR];
    for (int i = 0; i < this.addresses.length; ++i) {
      this.addresses[i] = this.multiChainClient.getNewAddress();
    }

    // best block at initialization is the genesis block
    this.multiChainBestBlock =
        this.multiChainClient.getBlock(this.multiChainClient.getBlockHash(0));
  }

  public static Props props(MultiChainClient client, RawTransactionConsumer... consumers) {
    return Props.create(MultiChainActor.class, () -> new MultiChainActor(client, consumers));
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();

    // kick off ensuring of sufficiently many UTXOs
    this.timers().startSingleTimer(
        this.multiChainEnsureUtxosTimerKey, new MultiChainEnsureUtxos(), Duration.ZERO);

    // kick off constant querying of the blockchain
    this.timers().startSingleTimer(
        this.multiChainQueryTimerKey, new MultiChainQuery(), Duration.ZERO);
  }

  @Override
  public void postStop() throws Exception {
    this.timers().cancel(this.multiChainEnsureUtxosTimerKey);
    this.timers().cancel(this.multiChainQueryTimerKey);

    super.postStop();
  }

  @Override
  public Receive createReceive() {
    final ReceiveBuilder builder = this.receiveBuilder();

    // make sure we always have enough UTXOs
    builder.match(MultiChainEnsureUtxos.class, this::multiChainEnsureUtxos);

    // query the blockchain for new blocks and transactions
    builder.match(MultiChainQuery.class, this::multiChainQuery);

    return builder.build();
  }

  private void multiChainEnsureUtxos(MultiChainEnsureUtxos ensureUtxos) {
    final BigDecimal utxoDividend = new BigDecimal(UTXO_SPLIT_FACTOR);
    final BigDecimal fee = new BigDecimal(UTXO_SPLIT_FACTOR / 100_000_000.0);

    final UnspentTransactionOutputList utxos = this.multiChainClient.listUnspent(0);
    LOG.trace("Got {} UTXOs", utxos.size());
    if (utxos.size() > 0 && utxos.size() < MIN_UTXOS) {
      for (UnspentTransactionOutput utxo : utxos) {
        if (utxo.spendable() && utxo.amount().compareTo(MIN_SPLITTABLE_AMOUNT) >= 0) {
          final TransactionInputList inputs = new TransactionInputList();
          inputs.add(
              new TransactionInput(utxo.txId(), utxo.vOut(), utxo.scriptPubKey(), utxo.amount()));

          BigDecimal remainingAmount = utxo.amount();
          final BigDecimal dividedAmount = remainingAmount.divide(utxoDividend);

          final TransactionOutputList outputs = new TransactionOutputList();
          for (int i = 1; i < this.addresses.length; ++i) {
            outputs.add(new TransactionOutput(this.addresses[i], dividedAmount, null));
            remainingAmount = remainingAmount.subtract(dividedAmount);
          }

          // use the first address for remaining amount and fee
          remainingAmount = remainingAmount.subtract(fee);
          outputs.add(new TransactionOutput(this.addresses[0], remainingAmount, null));

          LOG.trace("Splitting {}/{} ({}) into {} * {} and {}", utxo.txId(), utxo.vOut(),
              utxo.amount(), UTXO_SPLIT_FACTOR - 1, dividedAmount, remainingAmount);

          final String rawTransactionHex =
              this.multiChainClient.createRawTransaction(inputs, outputs);
          final String signedRawTransactionHex =
              this.multiChainClient.signRawTransactionWithWallet(rawTransactionHex, inputs);
          this.multiChainClient.sendRawTransaction(signedRawTransactionHex);
        }
      }

      this.timers().startSingleTimer(
          this.multiChainEnsureUtxosTimerKey, ensureUtxos, Duration.ZERO);
    } else {
      this.timers().startSingleTimer(this.multiChainEnsureUtxosTimerKey, ensureUtxos,
          Duration.ofMillis(ENSURE_UTXOS_INTERVAL));
    }
  }

  private void multiChainQuery(MultiChainQuery query) {
    // the chain should be queried for new transactions
    LOG.trace("Querying chain");

    // get the most recent block of the current best chain
    Block bestBlock = this.multiChainClient.getBlock(this.multiChainClient.getBestBlockHash());

    // sanity check
    if (bestBlock.height() < this.multiChainBestBlock.height()) {
      // block height should never decrease, fork or not
      LOG.error("Best chain contains fewer blocks than before ({} < {})", bestBlock.height(),
          this.multiChainBestBlock.height());
    } else if (!bestBlock.hash().equals(this.multiChainBestBlock.hash())) {
      // the new best block is different from the one we processed last time
      // 1) the new best block could be a successor to the last best block (no fork)
      // 2) the new best block could be a sibling of the last best block (fork)
      LOG.trace("Updating from block {} to {} (height {} to {})", this.multiChainBestBlock.hash(),
          bestBlock.hash(), this.multiChainBestBlock.height(), bestBlock.height());

      // build the branch of the best chain down to the last block we processed
      final Deque<Block> branch = new LinkedList<>();

      // the new best block must be processed anyway, successor or sibling
      branch.addFirst(bestBlock);

      // add all new blocks until they would be direct successors of the last best block
      while (branch.getFirst().height() > this.multiChainBestBlock.height() + 1) {
        branch.addFirst(this.multiChainClient.getBlock(branch.getFirst().previousBlockHash()));
      }

      // it the best chain's first block's predecessor's hash is not the best block we have last
      // processed, then a fork happened
      while (!branch.getFirst().previousBlockHash().equals(this.multiChainBestBlock.hash())) {
        // remove previously processed blocks
        LOG.trace("Unprocessing block {}", this.multiChainBestBlock.hash());

        for (String tx : this.multiChainBestBlock.tx()) {
          final RawTransaction rawTransaction = this.multiChainClient.getRawTransaction(tx);
          for (RawTransactionConsumer consumer : this.rawTransactionConsumers) {
            consumer.unconsumeRawTransaction(rawTransaction);
          }
        }

        // keep adding to the best chain while going back
        branch.addFirst(this.multiChainClient.getBlock(branch.getFirst().previousBlockHash()));
        this.multiChainBestBlock =
            this.multiChainClient.getBlock(this.multiChainBestBlock.previousBlockHash());
      }

      // now process all new blocks
      while (branch.size() > 0) {
        this.multiChainBestBlock = branch.pollFirst();
        LOG.trace("Processing block {} ({}) with {} tx", this.multiChainBestBlock.hash(),
            this.multiChainBestBlock.height(), this.multiChainBestBlock.tx().size());

        for (String txId : this.multiChainBestBlock.tx()) {
          final RawTransaction rawTransaction = this.multiChainClient.getRawTransaction(txId);
          for (RawTransactionConsumer consumer : this.rawTransactionConsumers) {
            consumer.consumeRawTransaction(rawTransaction);
          }
        }
      }
    }

    // get the best block again to see if it has changed in the meantime
    bestBlock = this.multiChainClient.getBlock(this.multiChainClient.getBestBlockHash());

    // schedule the next invocation after some time if the best block has not changed, otherwise
    // immediately
    if (bestBlock.hash().equals(this.multiChainBestBlock.hash())) {
      this.timers().startSingleTimer(
          this.multiChainQueryTimerKey, query, Duration.ofMillis(QUERY_INTERVAL));

      // signal to the consumers that we are done for now
      for (RawTransactionConsumer consumer : this.rawTransactionConsumers) {
        consumer.doneProcessingRawTransactions();
      }
    } else {
      this.timers().startSingleTimer(this.multiChainQueryTimerKey, query, Duration.ZERO);
    }
  }
}
