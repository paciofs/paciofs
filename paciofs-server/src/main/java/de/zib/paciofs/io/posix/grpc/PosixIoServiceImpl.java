/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.io.posix.grpc;

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
    LOG.trace("ping({})", in);
    return CompletableFuture.completedFuture(
        PingResponse.newBuilder().setPing(Ping.newBuilder().build()).build());
  }

  @Override
  public CompletionStage<StatResponse> stat(StatRequest in) {
    LOG.trace("stat({})", in);

    final Stat stat = Stat.newBuilder().build();
    final StatResponse response = StatResponse.newBuilder().setStat(stat).build();

    return CompletableFuture.completedFuture(response);
  }
}
