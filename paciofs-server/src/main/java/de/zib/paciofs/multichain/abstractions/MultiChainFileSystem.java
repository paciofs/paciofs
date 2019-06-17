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
import de.zib.paciofs.io.posix.grpc.messages.Dir;
import de.zib.paciofs.io.posix.grpc.messages.Mode;
import de.zib.paciofs.io.posix.grpc.messages.Stat;
import de.zib.paciofs.multichain.MultiChainUtil;
import de.zib.paciofs.multichain.actors.MultiChainActor;
import de.zib.paciofs.multichain.internal.MultiChainCommand;
import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

  private final Map<Volume, File> volumeRoots;

  private final File baseDir;

  /**
   * Construct a file system view on top of MultiChain.
   * @param client the MultiChain client to use
   * @param cluster the MultiChainCluster view to use
   */
  public MultiChainFileSystem(
      MultiChainRpcClient client, MultiChainCluster cluster, String baseDir) {
    this.clientUtil = new MultiChainUtil(client, FILE_SYSTEM_OP_RETURN_FEE, LOG);
    this.cluster = cluster;
    this.volumes = new ConcurrentHashMap<>();
    this.volumeRoots = new ConcurrentHashMap<>();
    this.baseDir = new File(baseDir);
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

      // TODO replace with mkDir and set permissions of / in the volume to the creating user
      // optimistically create the directory and add an entry in the map
      final File volumeRoot = new File(this.baseDir, volume.getName());
      if (!volumeRoot.mkdirs()) {
        LOG.warn("Could not create directory {} for volume {}", volumeRoot, volume.getName());
      }
      this.volumeRoots.put(volume, volumeRoot);

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

  /**
   * List the contents of a directory.
   * @param path path to the directory: volume:/path/to/dir
   * @return list of entries in that directory, can be zero-length
   * @throws FileNotFoundException if path does not exist or is not a directory
   */
  public List<Dir> readDir(String path) throws FileNotFoundException {
    final Volume volume = this.getVolumeFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    // TODO null check, otherwise we get access to /
    final File volumeRoot = this.volumeRoots.get(volume);
    final File directory = new File(volumeRoot, cleanedPath);
    if (!directory.exists() || !directory.isDirectory()) {
      throw new FileNotFoundException("Path " + cleanedPath + " on volume " + volume.getName()
          + " does not exist or is not a directory");
    }

    final List<Dir> dirEntries = new ArrayList<>();
    dirEntries.add(Dir.newBuilder().setName(".").build());
    dirEntries.add(Dir.newBuilder().setName("..").build());
    for (File entry : directory.listFiles()) {
      dirEntries.add(Dir.newBuilder().setName(entry.getName()).build());
    }

    return dirEntries;
  }

  /**
   * Get information for a file.
   * @param path path to the file, volume:/path/to/file
   * @param user user ID to use in the returned stat
   * @param group group ID to use in the returned stat
   * @return the file information
   * @throws FileNotFoundException if the file does not exist
   */
  public Stat stat(String path, int user, int group) throws FileNotFoundException {
    final Volume volume = this.getVolumeFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    // TODO null check, otherwise we get access to /
    final File volumeRoot = this.volumeRoots.get(volume);
    final File file = new File(volumeRoot, cleanedPath);
    if (!file.exists()) {
      throw new FileNotFoundException(
          "File " + cleanedPath + " on volume " + volume.getName() + " does not exist");
    }

    // TODO fill all stat fields
    final Stat.Builder builder = Stat.newBuilder();

    // TODO fix: all files belong to each calling user
    builder.setUid(user);
    builder.setGid(group);

    if (file.isDirectory()) {
      // drwxr-xr-x for directories
      builder.setMode(Mode.MODE_S_IFDIR_VALUE | Mode.MODE_S_IRWXU_VALUE | Mode.MODE_S_IRGRP_VALUE
          | Mode.MODE_S_IXGRP_VALUE | Mode.MODE_S_IROTH_VALUE | Mode.MODE_S_IXOTH_VALUE);
    } else {
      // -rw-r--r-- for files
      builder.setMode(Mode.MODE_S_IRUSR_VALUE | Mode.MODE_S_IWUSR_VALUE | Mode.MODE_S_IRGRP_VALUE
          | Mode.MODE_S_IROTH_VALUE);
    }

    return builder.build();
  }

  /**
   * Create a directory.
   * @param path path to the directory, volume:/path/to/dir
   * @return true if the directory was created, false otherwise
   */
  public boolean mkDir(String path) {
    final Volume volume = this.getVolumeFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    // TODO null check, otherwise we get access to /
    final File volumeRoot = this.volumeRoots.get(volume);
    final File directory = new File(volumeRoot, cleanedPath);

    final boolean success = directory.mkdir();
    if (success) {
      final String txId = this.clientUtil.sendRawTransaction(
          MultiChainCommand.MCC_IO_MKDIR, MultiChainUtil.encodeString(path));
      LOG.info("Directory {} was created in volume {} (transaction id: {})", cleanedPath,
          volume.getName(), txId);
    } else {
      LOG.warn("Directory {} could not be created in volume {}", cleanedPath, volume.getName());
    }

    return success;
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
          case MCC_IO_MKDIR: {
            final String path = MultiChainUtil.decodeString(data);
            this.mkDir(path);
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

  private Volume getVolumeFromPath(String path) {
    if (!path.contains(":")) {
      throw new IllegalArgumentException("No volume specified in path: " + path);
    }

    final String volumeName = path.split(":")[0];
    final Volume volume = this.volumes.get(volumeName);
    if (volume == null) {
      throw new IllegalArgumentException("Volume does not exist: " + volumeName);
    }

    return volume;
  }

  private static String removeVolumeFromPath(String path) {
    if (!path.contains(":")) {
      throw new IllegalArgumentException("No volume specified in path: " + path);
    }

    return path.split(":")[1];
  }
}
