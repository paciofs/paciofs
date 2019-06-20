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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
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

  // the equivalent of one satoshi
  private static final BigDecimal FILE_SYSTEM_OP_RETURN_FEE = new BigDecimal(1.0 / 100_000_000.0);

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
   * @throws FileAlreadyExistsException if the volume exists already
   * @throws IOException if an I/O error occurs
   */
  public Volume createVolume(Volume volume) throws IOException {
    this.checkClusterReadiness();

    // TODO synchronize the relevant parts here
    if (this.volumes.containsKey(volume.getName())) {
      throw new FileAlreadyExistsException(volume.getName());
    }

    // optimistically create the directory and add an entry in the map
    final File volumeRoot = new File(this.baseDir, volume.getName());
    if (!volumeRoot.mkdirs()) {
      throw new IOException(
          "Could not create directory " + volumeRoot + " for volume " + volume.getName());
    }

    final MultiChainData data = new MultiChainData();
    data.writeByteArray(volume.toByteArray());

    final String txId =
        this.clientUtil.sendRawTransaction(MultiChainCommand.MCC_VOLUME_CREATE, data);
    final Volume created = Volume.newBuilder(volume).setCreationTxId(txId).build();

    this.volumeRoots.put(created, volumeRoot);
    this.volumes.put(volume.getName(), created);

    LOG.debug("Volume {} was created", TextFormat.shortDebugString(created));

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
   * @throws NoSuchFileException if the path does not exist
   * @throws IOException if the file type is not supported
   */
  public Stat stat(String path, int user, int group) throws IOException {
    final File volumeRoot = this.getVolumeRootFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    final File file = new File(volumeRoot, cleanedPath);
    if (!file.exists()) {
      throw new NoSuchFileException(path);
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
   * @throws IllegalArgumentException if the file type is not supported
   * @throws NoSuchFileException if the volume does not exist
   * @throws FileAlreadyExistsException if the file exists already
   * @throws IOException if there is an error during creation
   */
  public void mkNod(String path, int mode, int dev) throws IOException {
    if ((mode & Mode.MODE_S_IFREG_VALUE) != Mode.MODE_S_IFREG_VALUE) {
      throw new IllegalArgumentException("Cannot create special file " + path);
    }

    final File volumeRoot = this.getVolumeRootFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);
    final File file = new File(volumeRoot, cleanedPath);

    if (file.exists()) {
      throw new FileAlreadyExistsException(path);
    }

    // touch the file
    new RandomAccessFile(file, "rw").close();

    final MultiChainData data = new MultiChainData();
    data.writeString(path);
    data.writeInt(mode);
    data.writeInt(dev);

    final String txId = this.clientUtil.sendRawTransaction(MultiChainCommand.MCC_IO_MKNOD, data);
    LOG.debug("Node {} was created (transaction id: {})", path, txId);
  }

  /**
   * Create a directory.
   * @param path path to the directory, volume:/path/to/dir
   * @param mode directory creation mode
   * @throws NoSuchFileException if the volume does not exist
   * @throws FileAlreadyExistsException if the directory exists already
   * @throws IOException if there is an error during creation
   */
  public void mkDir(String path, int mode) throws IOException {
    final File volumeRoot = this.getVolumeRootFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);
    final File directory = new File(volumeRoot, cleanedPath);

    if (directory.exists()) {
      throw new FileAlreadyExistsException(path);
    }

    if (directory.mkdir()) {
      final MultiChainData data = new MultiChainData();
      data.writeString(path);
      data.writeInt(mode);

      final String txId = this.clientUtil.sendRawTransaction(MultiChainCommand.MCC_IO_MKDIR, data);
      LOG.debug("Directory {} was created (transaction id: {})", path, txId);
    } else {
      throw new IOException("Could not create directory " + path);
    }
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
   * @throws NoSuchFileException if the path does not exist
   */
  public long open(String path, int flags) throws NoSuchFileException {
    final File volumeRoot = this.getVolumeRootFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);
    final File file = new File(volumeRoot, cleanedPath);

    if (!file.exists()) {
      throw new NoSuchFileException(path);
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
   * @throws NoSuchFileException if the path does not exist
   * @throws IOException if there is an error during reading
   */
  public int read(String path, ByteBuffer destination, long offset, long fh) throws IOException {
    final File volumeRoot = this.getVolumeRootFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);
    final RandomAccessFile file;
    try {
      file = new RandomAccessFile(new File(volumeRoot, cleanedPath), "r");
    } catch (FileNotFoundException e) {
      throw new NoSuchFileException(path, null, e.getMessage());
    }

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
   * @throws NoSuchFileException if the path does not exist
   * @throws IOException if there is an error during writing
   */
  public int write(String path, ByteBuffer source, long offset, long fh) throws IOException {
    final File volumeRoot = this.getVolumeRootFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);
    final RandomAccessFile file;
    try {
      file = new RandomAccessFile(new File(volumeRoot, cleanedPath), "rw");
    } catch (FileNotFoundException e) {
      throw new NoSuchFileException(path, null, e.getMessage());
    }

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
    LOG.debug("Wrote {} bytes from {} to {} (sha256: {}) to file {} (transaction id: {})", n,
        offset, offset + n, Hex.encodeHexString(sha256, true), path, txId);

    return n;
  }

  /**
   * List the contents of a directory.
   * @param path path to the directory: volume:/path/to/dir
   * @return list of entries in that directory, can be zero-length
   * @throws NoSuchFileException if path does not exist
   * @throws NotDirectoryException if the path is not a directory
   * @throws IOException if there is an error during listing
   */
  public List<Dir> readDir(String path) throws IOException {
    final File volumeRoot = this.getVolumeRootFromPath(path);
    final String cleanedPath = removeVolumeFromPath(path);

    final File directory = new File(volumeRoot, cleanedPath);
    if (!directory.exists()) {
      throw new NoSuchFileException(path);
    }
    if (!directory.isDirectory()) {
      throw new NotDirectoryException(path);
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
   * Shortcut for mkNod and open.
   * @see #mkNod(String, int, int)
   * @see #open(String, int)
   */
  public long create(String path, int mode, int flags) throws IOException {
    this.mkNod(path, mode, 0);
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
          case MCC_IO_WRITE: {
            // TODO obtain the relevant data from other nodes in the cluster
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

  private void checkClusterReadiness() {
    if (!this.cluster.ready()) {
      throw new IllegalStateException("Cluster is not ready");
    }
  }

  private File getVolumeRootFromPath(String path) throws NoSuchFileException {
    final Volume volume = this.getVolumeFromPath(path);
    final File volumeRoot = this.volumeRoots.get(volume);
    if (volumeRoot == null) {
      throw new NoSuchFileException(volume.getName());
    }

    return volumeRoot;
  }

  private Volume getVolumeFromPath(String path) throws NoSuchFileException {
    if (!path.contains(":")) {
      throw new InvalidPathException(path, "No volume specified in path");
    }

    final String volumeName = path.split(":")[0];
    final Volume volume = this.volumes.get(volumeName);
    if (volume == null) {
      throw new NoSuchFileException(volumeName);
    }

    return volume;
  }

  private static String removeVolumeFromPath(String path) {
    if (!path.contains(":")) {
      throw new InvalidPathException(path, "No volume specified in path");
    }

    return path.split(":")[1];
  }
}
