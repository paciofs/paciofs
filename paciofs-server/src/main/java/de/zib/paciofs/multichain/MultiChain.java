/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.cluster.Member;
import com.typesafe.config.Config;
import de.zib.paciofs.cluster.AbstractClusterDomainEventListener;
import de.zib.paciofs.logging.Markers;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import wf.bitcoin.javabitcoindrpcclient.GenericRpcException;

public class MultiChain extends AbstractClusterDomainEventListener {
  private static final String PACIOFS_MUTLICHAIN_CLIENT_KEY = "paciofs.multichain-client";
  private static final String PACIOFS_MULTICHAIN_CLIENT_CLASS_KEY =
      PACIOFS_MUTLICHAIN_CLIENT_KEY + ".class";

  private static final Logger LOG = LoggerFactory.getLogger(MultiChain.class);

  private final MultiChainDaemonRpcClient client;

  private final Config config;

  private MultiChain() {
    this.config = this.getContext().system().settings().config();

    // may throw if key is empty or null
    final String multichainClientClassName =
        this.config.getString(PACIOFS_MULTICHAIN_CLIENT_CLASS_KEY);

    // find the class implementing the BitcoindRpcClient interface
    final Class<MultiChainDaemonRpcClient> multiChainDaemonRpcClientClass;
    try {
      multiChainDaemonRpcClientClass =
          (Class<MultiChainDaemonRpcClient>) Class.forName(multichainClientClassName);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(multichainClientClassName + " not found", e);
    }

    // obtain an instance from the two argument constructor
    final Constructor<MultiChainDaemonRpcClient> constructor;
    try {
      constructor = multiChainDaemonRpcClientClass.getDeclaredConstructor(Config.class);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Could not find constructor with single argument of type "
          + Config.class.getName() + " in " + multichainClientClassName);
    }

    try {
      this.client = constructor.newInstance(this.config.getConfig(PACIOFS_MUTLICHAIN_CLIENT_KEY));
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException("Could not instantiate " + multichainClientClassName, e);
    }

    // shut down the multichain before the actor
    CoordinatedShutdown.get(this.getContext().getSystem()).addJvmShutdownHook(this.client::stop);
  }

  public static Props props() {
    return Props.create(MultiChain.class, MultiChain::new);
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();

    // TODO if this fails, should we fail the entire actor system?
    // warm up the client
    final MultiChainDaemonRpcClient.BlockChainInfo bci = this.client.getBlockChainInfo();
    LOG.info("Connected to chain {}", bci.chain());
  }

  @Override
  public void postStop() throws Exception {
    this.client.stop();
    super.postStop();
  }

  @Override
  protected void memberDowned(Member member) {
    super.memberDowned(member);
    this.removeNode(member);
  }

  @Override
  protected void memberExited(Member member) {
    super.memberExited(member);
    this.removeNode(member);
  }

  @Override
  protected void memberLeft(Member member) {
    super.memberLeft(member);
    this.removeNode(member);
  }

  @Override
  protected void memberRemoved(Member member) {
    super.memberRemoved(member);
    this.removeNode(member);
  }

  @Override
  protected void memberUp(Member member) {
    super.memberUp(member);
    this.addNode(member);
  }

  @Override
  protected void memberWeaklyUp(Member member) {
    super.memberWeaklyUp(member);
    // TODO is weakly up enough to add this node?
  }

  private void addNode(Member member) {
    final Option<String> host = member.address().host();
    if (host.isEmpty()) {
      LOG.warn("Cannot add node with empty host: {}", member);
    } else {
      try {
        LOG.trace("Adding node: {}", host.get());
        this.client.addNode(host.get(), "add");
        LOG.trace("Added node: {}", host.get());
      } catch (GenericRpcException e) {
        LOG.warn("Adding node failed: {}", e.getMessage());
        LOG.warn(Markers.EXCEPTION, "Adding node failed", e);
      }
    }

    // have MultiChain measure latency and backlog
    this.client.ping();
  }

  private void removeNode(Member member) {
    final Option<String> host = member.address().host();
    if (host.isEmpty()) {
      LOG.warn("Cannot remove with empty host: {}", member);
    } else {
      try {
        LOG.trace("Removing node: {}", host.get());
        this.client.addNode(host.get(), "remove");
        LOG.trace("Removed node: {}", host.get());
      } catch (GenericRpcException e) {
        LOG.warn("Removing node failed: {}", e.getMessage());
        LOG.warn(Markers.EXCEPTION, "Removing node failed", e);
      }
    }

    // have MultiChain measure latency and backlog
    this.client.ping();
  }
}
