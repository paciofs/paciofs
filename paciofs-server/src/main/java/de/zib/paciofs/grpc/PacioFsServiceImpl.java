/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.grpc;

import akka.grpc.GrpcServiceException;
import akka.grpc.javadsl.Metadata;
import de.zib.paciofs.grpc.messages.Ping;
import de.zib.paciofs.logging.Markers;
import de.zib.paciofs.multichain.abstractions.MultiChainFileSystem;
import io.grpc.Status;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacioFsServiceImpl implements PacioFsServicePowerApi {
  private static final Logger LOG = LoggerFactory.getLogger(PacioFsServiceImpl.class);

  private final MultiChainFileSystem multiChainFileSystem;

  public PacioFsServiceImpl(MultiChainFileSystem fileSystem) {
    this.multiChainFileSystem = fileSystem;
  }

  @Override
  public CompletionStage<CreateVolumeResponse> createVolume(
      CreateVolumeRequest in, Metadata metadata) {
    PacioFsGrpcUtil.traceMessages(LOG, "createVolume({})", in);

    final CreateVolumeResponse.Builder builder = CreateVolumeResponse.newBuilder();
    try {
      builder.setVolume(this.multiChainFileSystem.createVolume(in.getVolume()));
    } catch (FileAlreadyExistsException e) {
      throw new GrpcServiceException(Status.ALREADY_EXISTS);
    } catch (IOException e) {
      LOG.warn(Markers.EXCEPTION, "Could not create volume {}", in.getVolume().getName(), e);
      throw new GrpcServiceException(
          Status.UNKNOWN.withCause(e).augmentDescription(e.getMessage()));
    }

    final CreateVolumeResponse out = builder.build();

    PacioFsGrpcUtil.traceMessages(LOG, "createVolume({}): {}", in, out);
    return CompletableFuture.completedFuture(out);
  }

  @Override
  public CompletionStage<PingResponse> ping(PingRequest in, Metadata metadata) {
    PacioFsGrpcUtil.traceMessages(LOG, "ping({})", in);

    final Ping ping = Ping.newBuilder().build();
    final PingResponse out = PingResponse.newBuilder().setPing(ping).build();

    PacioFsGrpcUtil.traceMessages(LOG, "ping({}): {}", in, out);
    return CompletableFuture.completedFuture(out);
  }
}
