/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.io.posix.grpc;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.TextFormat;
import de.zib.paciofs.io.posix.grpc.messages.Dir;
import de.zib.paciofs.io.posix.grpc.messages.Errno;
import de.zib.paciofs.io.posix.grpc.messages.Mode;
import de.zib.paciofs.io.posix.grpc.messages.Ping;
import de.zib.paciofs.io.posix.grpc.messages.Stat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PosixIoServiceImpl implements PosixIoService {
  private static final Logger LOG = LoggerFactory.getLogger(PosixIoServiceImpl.class);

  public PosixIoServiceImpl() {}

  @Override
  public CompletionStage<PingResponse> ping(PingRequest in) {
    trace("ping({})", in);

    final Ping ping = Ping.newBuilder().build();
    final PingResponse out = PingResponse.newBuilder().setPing(ping).build();

    trace("ping({}):{}", in, out);
    return CompletableFuture.completedFuture(out);
  }

  @Override
  public CompletionStage<ReadDirResponse> readDir(ReadDirRequest in) {
    trace("readDir({})", in);

    Errno error = Errno.ERRNO_ESUCCESS;
    final ReadDirResponse.Builder builder = ReadDirResponse.newBuilder();
    if ("/".equals(in.getPath())) {
      builder.addDirs(Dir.newBuilder().setName(".").build());
      builder.addDirs(Dir.newBuilder().setName("..").build());
    } else {
      error = Errno.ERRNO_ENOENT;
    }

    final ReadDirResponse out = builder.setError(error).build();

    trace("readDir({}):{}", in, out);
    return CompletableFuture.completedFuture(out);
  }

  @Override
  public CompletionStage<StatResponse> stat(StatRequest in) {
    trace("stat({})", in);

    Errno error = Errno.ERRNO_ESUCCESS;
    final Stat.Builder builder = Stat.newBuilder();
    if ("/".equals(in.getPath())) {
      // directory | rwxr-xr-x
      builder.setMode(Mode.MODE_S_IFDIR_VALUE | Mode.MODE_S_IRWXU_VALUE | Mode.MODE_S_IRGRP_VALUE
          | Mode.MODE_S_IXGRP_VALUE | Mode.MODE_S_IROTH_VALUE | Mode.MODE_S_IXOTH_VALUE);
      // . and ..
      builder.setNlink(2);
    } else {
      error = Errno.ERRNO_ENOENT;
    }

    final StatResponse out =
        StatResponse.newBuilder().setStat(builder.build()).setError(error).build();

    trace("stat({}):{}", in, out);
    return CompletableFuture.completedFuture(out);
  }

  private static void trace(String formatString, AbstractMessage... messages) {
    // building the string representations is expensive, so guard it
    if (LOG.isTraceEnabled()) {
      final String[] messageStrings = new String[messages.length];
      for (int i = 0; i < messages.length; ++i) {
        messageStrings[i] = TextFormat.shortDebugString(messages[i]);
      }
      LOG.trace(formatString, messageStrings);
    }
  }
}
