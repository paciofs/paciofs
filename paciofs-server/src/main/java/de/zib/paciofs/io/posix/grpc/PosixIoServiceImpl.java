/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.io.posix.grpc;

import de.zib.paciofs.grpc.PacioFsGrpc;
import de.zib.paciofs.grpc.messages.Ping;
import de.zib.paciofs.io.posix.grpc.messages.Dir;
import de.zib.paciofs.io.posix.grpc.messages.Errno;
import de.zib.paciofs.io.posix.grpc.messages.Mode;
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
    PacioFsGrpc.traceRequest(LOG, "ping({})", in);

    final Ping ping = Ping.newBuilder().build();
    final PingResponse out = PingResponse.newBuilder().setPing(ping).build();

    PacioFsGrpc.traceRequest(LOG, "ping({}): {}", in, out);
    return CompletableFuture.completedFuture(out);
  }

  @Override
  public CompletionStage<ReadDirResponse> readDir(ReadDirRequest in) {
    PacioFsGrpc.traceRequest(LOG, "readDir({})", in);

    Errno error = Errno.ERRNO_ESUCCESS;
    final ReadDirResponse.Builder builder = ReadDirResponse.newBuilder();
    if ("/".equals(in.getPath())) {
      builder.addDirs(Dir.newBuilder().setName(".").build());
      builder.addDirs(Dir.newBuilder().setName("..").build());
    } else {
      error = Errno.ERRNO_ENOENT;
    }

    final ReadDirResponse out = builder.setError(error).build();

    PacioFsGrpc.traceRequest(LOG, "readDir({}): {}", in, out);
    return CompletableFuture.completedFuture(out);
  }

  @Override
  public CompletionStage<StatResponse> stat(StatRequest in) {
    PacioFsGrpc.traceRequest(LOG, "stat({})", in);

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

    PacioFsGrpc.traceRequest(LOG, "stat({}): {}", in, out);
    return CompletableFuture.completedFuture(out);
  }
}
