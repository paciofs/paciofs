/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.grpc;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import de.zib.paciofs.grpc.messages.Ping;
import de.zib.paciofs.grpc.messages.Volume;
import de.zib.paciofs.logging.Markers;
import de.zib.paciofs.multichain.actors.MultiChainStreamBroadcastActor;
import de.zib.paciofs.multichain.rpc.MultiChainRpcClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCException;

public class PacioFsServiceImpl implements PacioFsService {
  private static final Logger LOG = LoggerFactory.getLogger(PacioFsServiceImpl.class);

  private final MultiChainRpcClient multiChainClient;

  private final ActorRef multiChainStreamBroadcastActor;

  public PacioFsServiceImpl(
      MultiChainRpcClient multiChainClient, ActorRef multiChainStreamBroadcastActor) {
    this.multiChainClient = multiChainClient;
    this.multiChainStreamBroadcastActor = multiChainStreamBroadcastActor;
  }

  @Override
  public CompletionStage<CreateVolumeResponse> createVolume(CreateVolumeRequest in) {
    PacioFsGrpcUtil.traceMessages(LOG, "createVolume({})", in);

    final String createStreamTxId;
    try {
      createStreamTxId = this.multiChainClient.createStream(in.getVolume().getName(), true);
    } catch (BitcoinRPCException e) {
      LOG.debug("Error creating stream: {}", e.getMessage());
      LOG.debug(Markers.EXCEPTION, "Error creating stream", e);
      throw PacioFsGrpcUtil.toGrpcServiceException(e);
    }

    // TODO make request-response instead of fire-and-forget
    final CompletableFuture<Object> subscribeToStreamBroadcast =
        Patterns
            .ask(this.multiChainStreamBroadcastActor,
                new MultiChainStreamBroadcastActor.SubscribeToStream(createStreamTxId),
                Duration.ofMillis(1000))
            .toCompletableFuture();

    final Volume volume = Volume.newBuilder().build();
    final CreateVolumeResponse out = CreateVolumeResponse.newBuilder().setVolume(volume).build();

    PacioFsGrpcUtil.traceMessages(LOG, "createVolume({}): {}", in, out);
    return CompletableFuture.completedFuture(out);
  }

  @Override
  public CompletionStage<PingResponse> ping(PingRequest in) {
    PacioFsGrpcUtil.traceMessages(LOG, "ping({})", in);

    final Ping ping = Ping.newBuilder().build();
    final PingResponse out = PingResponse.newBuilder().setPing(ping).build();

    PacioFsGrpcUtil.traceMessages(LOG, "ping({}): {}", in, out);
    return CompletableFuture.completedFuture(out);
  }
}
