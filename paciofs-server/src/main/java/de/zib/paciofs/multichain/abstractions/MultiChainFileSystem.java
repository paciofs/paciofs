/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.abstractions;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import de.zib.paciofs.grpc.messages.Volume;
import de.zib.paciofs.multichain.MultiChainUtil;
import de.zib.paciofs.multichain.actors.MultiChainActor;
import de.zib.paciofs.multichain.internal.MultiChainCommand;
import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

public class MultiChainFileSystem implements MultiChainActor.RawTransactionConsumer {
  private static final Logger LOG = LoggerFactory.getLogger(MultiChainFileSystem.class);

  private static final BigDecimal FILE_SYSTEM_OP_RETURN_FEE = new BigDecimal(1);

  private final MultiChainUtil clientUtil;

  private final MultiChainCluster cluster;

  private final Map<String, Volume> volumes;

  /**
   * Construct a file system view on top of MultiChain.
   * @param client the MultiChain client to use
   * @param cluster the MultiChainCluster view to use
   */
  public MultiChainFileSystem(MultiChainRpcClient client, MultiChainCluster cluster) {
    this.clientUtil = new MultiChainUtil(client, FILE_SYSTEM_OP_RETURN_FEE, 0, LOG);
    this.cluster = cluster;
    this.volumes = new ConcurrentHashMap<>();
  }

  /**
   * Create a volume, sending it to MultiChain.
   * @param volume the volume to create
   * @return the created volume, along with its MultiChain transaction id
   */
  public Volume createVolume(Volume volume) {
    // TODO check cluster for readiness
    // TODO check for same name of volume
    Volume created = this.volumes.merge(volume.getName(), volume, (old, toAdd) -> {
      if ("".equals(old.getCreationTxId()) && !"".equals(toAdd.getCreationTxId())) {
        // if the new volume has a creation transaction ID and the old one does not, then update
        return toAdd;
      }

      // volumes are either the same, or the new one does not have a creation transaction ID
      return old;
    });

    if (created == volume) {
      LOG.info("Volume {} was added to cluster", TextFormat.shortDebugString(volume));

      if ("".equals(volume.getCreationTxId())) {
        // send the volume to the chain as we have not seen it before
        final String txId = this.clientUtil.sendRawTransaction(
            MultiChainCommand.MCC_VOLUME_CREATE, volume.toByteArray());
        created = Volume.newBuilder(volume).setCreationTxId(txId).build();
      }
    } else {
      LOG.warn("Volume {} is already present in cluster", TextFormat.shortDebugString(volume));
    }

    return created;
  }

  public Volume deleteVolume(Volume volume) {
    // TODO implement
    throw new UnsupportedOperationException();
  }

  @Override
  public void consumeRawTransaction(BitcoindRpcClient.RawTransaction rawTransaction) {
    LOG.trace("Received raw tx: {}", rawTransaction.txId());

    this.clientUtil.processRawTransaction(rawTransaction, (command, data) -> {
      try {
        switch (command) {
          case MCC_VOLUME_CREATE: {
            final Volume volume = Volume.newBuilder(Volume.parseFrom(data))
                                      .setCreationTxId(rawTransaction.txId())
                                      .build();
            this.createVolume(volume);

            break;
          }
          case MCC_VOLUME_DELETE: {
            final Volume volume = Volume.parseFrom(data);
            this.deleteVolume(volume);
            break;
          }
          default:
            // not for us, ignore
            break;
        }
      } catch (InvalidProtocolBufferException e) {
        // should not happen because at this point we know what data to expect
        MultiChainFileSystem.LOG.error("Error parsing data", e);
      }
    });
  }

  @Override
  public void unconsumeRawTransaction(BitcoindRpcClient.RawTransaction rawTransaction) {
    LOG.trace("Received raw tx for removal: {}", rawTransaction.txId());
  }
}
