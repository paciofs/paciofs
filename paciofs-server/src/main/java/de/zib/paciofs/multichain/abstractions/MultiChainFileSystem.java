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
import de.zib.paciofs.logging.Markers;
import de.zib.paciofs.multichain.MultiChainData;
import de.zib.paciofs.multichain.MultiChainUtil;
import de.zib.paciofs.multichain.actors.MultiChainActor;
import de.zib.paciofs.multichain.internal.MultiChainCommand;
import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
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
        final MultiChainData data = new MultiChainData();
        data.writeByteArray(volume.toByteArray());

        final String txId =
            this.clientUtil.sendRawTransaction(MultiChainCommand.MCC_VOLUME_CREATE, data);
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
   * Get information for a file.
   * @param path path to the file, volume:/path/to/file
   * @param user user ID to use in the returned stat
   * @param group group ID to use in the returned stat
   * @return the file information
   * @throws IOException if the file does not exist or the file type is not supported
   */
  public Stat stat(String path, int user, int group) throws IOException {
    final Volume volume = this.getVolumeFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    // TODO null check, otherwise we get access to /
    final File volumeRoot = this.volumeRoots.get(volume);
    final File file = new File(volumeRoot, cleanedPath);
    if (!file.exists()) {
      throw new FileNotFoundException("Path " + path + " does not exist");
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
    } else if (file.isFile()) {
      // -rw-r--r-- for regular files
      builder.setMode(Mode.MODE_S_IFREG_VALUE | Mode.MODE_S_IRUSR_VALUE | Mode.MODE_S_IWUSR_VALUE
          | Mode.MODE_S_IRGRP_VALUE | Mode.MODE_S_IROTH_VALUE);

      builder.setSize(file.length());
    } else {
      throw new IOException("Illegal file type for " + path);
    }

    return builder.build();
  }

  /**
   * Create a file.
   * @param path path to the file to create, volume:/path/to/file
   * @param mode file creation mode
   * @param dev major and minor version for device special files
   * @return true if the file was created, false otherwise
   * @throws IOException if any error occurs when writing the file
   */
  public boolean mkNod(String path, int mode, int dev) throws IOException {
    if ((mode & Mode.MODE_S_IFREG_VALUE) != Mode.MODE_S_IFREG_VALUE) {
      throw new IllegalArgumentException("Cannot create special file " + path);
    }

    final Volume volume = this.getVolumeFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    // TODO null check, otherwise we get access to /
    final File volumeRoot = this.volumeRoots.get(volume);
    final File file = new File(volumeRoot, cleanedPath);

    if (file.exists()) {
      return false;
    }

    // touch the file
    new RandomAccessFile(file, "rw").close();

    final MultiChainData data = new MultiChainData();
    data.writeString(path);
    data.writeInt(mode);
    data.writeInt(dev);

    final String txId = this.clientUtil.sendRawTransaction(MultiChainCommand.MCC_IO_MKNOD, data);
    LOG.info("Node {} was created in volume {} (transaction id: {})", cleanedPath, volume.getName(),
        txId);

    return true;
  }

  /**
   * Create a directory.
   * @param path path to the directory, volume:/path/to/dir
   * @param mode directory creation mode
   * @return true if the directory was created, false otherwise
   */
  public boolean mkDir(String path, int mode) {
    final Volume volume = this.getVolumeFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    // TODO null check, otherwise we get access to /
    final File volumeRoot = this.volumeRoots.get(volume);
    final File directory = new File(volumeRoot, cleanedPath);

    if (directory.exists()) {
      return false;
    }

    final boolean success = directory.mkdir();
    if (success) {
      final MultiChainData data = new MultiChainData();
      data.writeString(path);
      data.writeInt(mode);

      final String txId = this.clientUtil.sendRawTransaction(MultiChainCommand.MCC_IO_MKDIR, data);
      LOG.info("Directory {} was created in volume {} (transaction id: {})", cleanedPath,
          volume.getName(), txId);
    } else {
      LOG.warn("Directory {} could not be created in volume {}", cleanedPath, volume.getName());
    }

    return success;
  }

  /**
   * Change a file's mode.
   * @param path path to the file, volume:/path/to/file
   * @param mode the new mode
   * @return true on success, false otherwise
   */
  public boolean chMod(String path, int mode) {
    // TODO implement
    return true;
  }

  /**
   * Change a file's owner.
   * @param path path to the file, volume:/path/to/file
   * @param uid the new owner
   * @param gid the new group
   * @return true on success, false otherwise
   */
  public boolean chOwn(String path, int uid, int gid) {
    // TODO implement
    return true;
  }

  /**
   * Open a file.
   * @param path path to the file: volume:/path/to/file
   * @param flags open flags
   * @return a file handle
   * @throws FileNotFoundException if the path does not exist
   */
  public long open(String path, int flags) throws FileNotFoundException {
    final Volume volume = this.getVolumeFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    // TODO null check, otherwise we get access to /
    final File volumeRoot = this.volumeRoots.get(volume);
    final File file = new File(volumeRoot, cleanedPath);

    if (!file.exists()) {
      throw new FileNotFoundException("Path " + path + " does not exist or is not a directory");
    }

    // TODO use a more meaningful file handle
    return path.hashCode();
  }

  /**
   * Read from a file.
   * @param path path to the file: volume:/path/to/file
   * @param destination buffer to read contents into
   * @param offset position in the file
   * @param fh file handle as returned by {@link #open(String, int)}
   * @return the number of bytes read, -1 on EOF
   * @throws IOException if the path does not exist or on IO errors
   */
  public int read(String path, ByteBuffer destination, long offset, long fh) throws IOException {
    final Volume volume = this.getVolumeFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    // TODO null check, otherwise we get access to /
    final File volumeRoot = this.volumeRoots.get(volume);
    final RandomAccessFile file = new RandomAccessFile(new File(volumeRoot, cleanedPath), "r");

    final FileChannel channel = file.getChannel();
    final int n = channel.read(destination, offset);
    channel.close();
    return n;
  }

  /**
   * Write to a file.
   * @param path path to the file: volume:/path/to/file
   * @param source buffer to write contents from
   * @param offset position in the file
   * @param fh file handle as returned by {@link #open(String, int)}
   * @return the number of bytes written
   * @throws IOException if the path does not exist or on IO errors
   */
  public int write(String path, ByteBuffer source, long offset, long fh) throws IOException {
    final Volume volume = this.getVolumeFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    // TODO null check, otherwise we get access to /
    final File volumeRoot = this.volumeRoots.get(volume);
    final RandomAccessFile file = new RandomAccessFile(new File(volumeRoot, cleanedPath), "rw");

    final FileChannel channel = file.getChannel();
    final int n = channel.write(source.slice(), offset);
    channel.close();

    final ByteBuffer sourceWritten = source.slice().limit(n);
    final byte[] sha256 = DigestUtils.digest(DigestUtils.getSha256Digest(), sourceWritten);

    final MultiChainData data = new MultiChainData();
    data.writeString(path);
    data.writeLong(offset);
    data.writeInt(n);
    data.writeByteArray(sha256);

    final String txId = this.clientUtil.sendRawTransaction(MultiChainCommand.MCC_IO_WRITE, data);
    LOG.info("Wrote {} bytes (sha256: {}) to file {} on volume {} (transaction id: {})", n,
        Hex.encodeHexString(sha256, true), cleanedPath, volume.getName(), txId);

    return n;
  }

  /**
   * List the contents of a directory.
   * @param path path to the directory: volume:/path/to/dir
   * @return list of entries in that directory, can be zero-length
   * @throws FileNotFoundException if path does not exist or is not a directory or there is an error
   *     during listing
   */
  public List<Dir> readDir(String path) throws IOException {
    final Volume volume = this.getVolumeFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    // TODO null check, otherwise we get access to /
    final File volumeRoot = this.volumeRoots.get(volume);
    final File directory = new File(volumeRoot, cleanedPath);
    if (!directory.exists() || !directory.isDirectory()) {
      throw new FileNotFoundException("Path " + path + " does not exist or is not a directory");
    }

    final List<Dir> dirEntries = new ArrayList<>();
    dirEntries.add(Dir.newBuilder().setName(".").build());
    dirEntries.add(Dir.newBuilder().setName("..").build());

    final File[] entries = directory.listFiles();
    if (entries == null) {
      throw new IOException("Error listing " + path);
    }

    for (File entry : entries) {
      dirEntries.add(Dir.newBuilder().setName(entry.getName()).build());
    }

    return dirEntries;
  }

  /**
   * Shortcut for {@link #mkNod(String, int, int)} and {@link #open(String, int)}.
   * @param path path to the file to create: volume:/path/to/file
   * @param mode file creation mode
   * @param flags open flags
   * @return a file handle
   * @throws IOException if the file could not be created
   */
  public long create(String path, int mode, int flags) throws IOException {
    if (!this.mkNod(path, mode, 0)) {
      throw new IOException("Could not create file " + path);
    }

    return this.open(path, flags);
  }

  @Override
  public void consumeRawTransaction(BitcoindRpcClient.RawTransaction rawTransaction) {
    LOG.trace("Received raw tx: {}", rawTransaction.txId());

    this.clientUtil.processRawTransaction(rawTransaction, (command, data) -> {
      try {
        switch (command) {
          case MCC_VOLUME_CREATE: {
            final Volume volume = Volume.newBuilder(Volume.parseFrom(data.readByteArray()))
                                      .setCreationTxId(rawTransaction.txId())
                                      .build();
            this.createVolume(volume);

            break;
          }
          case MCC_VOLUME_DELETE: {
            final Volume volume = Volume.parseFrom(data.readByteArray());
            this.deleteVolume(volume);
            break;
          }
          case MCC_IO_MKNOD: {
            final String path = data.readString();
            final int mode = data.readInt();
            final int dev = data.readInt();
            this.mkNod(path, mode, dev);
            break;
          }
          case MCC_IO_MKDIR: {
            final String path = data.readString();
            final int mode = data.readInt();
            this.mkDir(path, mode);
            break;
          }
          default:
            // not for us, ignore
            break;
        }
      } catch (InvalidProtocolBufferException e) {
        // should not happen because at this point we know what data to expect
        MultiChainFileSystem.LOG.error(Markers.EXCEPTION, "Error parsing data", e);
      } catch (IOException e) {
        MultiChainFileSystem.LOG.error(
            Markers.EXCEPTION, "Could not process command {}", command, e);
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
