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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCException;

public class PacioFsServiceImpl implements PacioFsService {
  private static final Logger LOG = LoggerFactory.getLogger(PacioFsServiceImpl.class);

  private static final long MULTICHAIN_BROADCAST_TIMEOUT = 1000;

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

    // represent volumes as MultiChain streams
    final String createStreamTxId;
    try {
      createStreamTxId = this.multiChainClient.createStream(in.getVolume().getName(), true);
    } catch (BitcoinRPCException e) {
      LOG.debug("Error creating stream: {}", e.getMessage());
      LOG.debug(Markers.EXCEPTION, "Error creating stream", e);
      throw PacioFsGrpcUtil.toGrpcServiceException(e);
    }

    // tell the other MultiChain instances in the cluster to subscribe to the new stream
    final CompletableFuture<Object> subscribeToStreamBroadcast =
        Patterns
            .ask(this.multiChainStreamBroadcastActor,
                new MultiChainStreamBroadcastActor.SubscribeToStream(createStreamTxId),
                Duration.ofMillis(MULTICHAIN_BROADCAST_TIMEOUT))
            .toCompletableFuture();

    try {
      final Object result =
          subscribeToStreamBroadcast.get(MULTICHAIN_BROADCAST_TIMEOUT, TimeUnit.MILLISECONDS);
      if (!(result instanceof MultiChainStreamBroadcastActor.SubscribeToStream)) {
        LOG.warn("Unexpected result from stream subscription broadcast: {}", result);
      }
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      LOG.warn("Could not get result from stream subscription broadcast: {}", e.getMessage());
      LOG.warn(Markers.EXCEPTION, "Could not get result from stream subscription broadcast", e);
    }

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
