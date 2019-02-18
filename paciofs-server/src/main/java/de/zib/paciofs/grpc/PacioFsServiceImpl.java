/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.grpc;

import de.zib.paciofs.grpc.messages.Ping;
import de.zib.paciofs.grpc.messages.Volume;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacioFsServiceImpl implements PacioFsService {
  private static final Logger LOG = LoggerFactory.getLogger(PacioFsServiceImpl.class);

  public PacioFsServiceImpl() {}

  @Override
  public CompletionStage<CreateVolumeResponse> createVolume(CreateVolumeRequest in) {
    PacioFsGrpc.traceRequest(LOG, "createVolume({})", in);

    final Volume volume = Volume.newBuilder().build();
    final CreateVolumeResponse out = CreateVolumeResponse.newBuilder().setVolume(volume).build();

    PacioFsGrpc.traceRequest(LOG, "createVolume({}): {}", in, out);
    return CompletableFuture.completedFuture(out);
  }

  @Override
  public CompletionStage<PingResponse> ping(PingRequest in) {
    PacioFsGrpc.traceRequest(LOG, "ping({})", in);

    final Ping ping = Ping.newBuilder().build();
    final PingResponse out = PingResponse.newBuilder().setPing(ping).build();

    PacioFsGrpc.traceRequest(LOG, "ping({}): {}", in, out);
    return CompletableFuture.completedFuture(out);
  }
}
