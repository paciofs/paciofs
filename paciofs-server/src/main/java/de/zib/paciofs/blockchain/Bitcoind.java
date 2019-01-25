/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.blockchain;

import akka.actor.AbstractActor;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

public class Bitcoind extends AbstractActor {
  private static final String PACIOFS_BITCOIND_CLIENT_KEY = "paciofs.bitcoind-client";
  private static final String PACIOFS_BITCOIND_CLIENT_CLASS_KEY =
      PACIOFS_BITCOIND_CLIENT_KEY + ".class";

  private final LoggingAdapter log;

  private final BitcoindRpcClient client;

  private Bitcoind() {
    this.log = Logging.getLogger(this.getContext().system(), this);

    final Config config = this.getContext().system().settings().config();

    // may throw if key is empty or null
    final String bitcoindClientClassName = config.getString(PACIOFS_BITCOIND_CLIENT_CLASS_KEY);

    // find the class implementing the BitcoindRpcClient interface
    final Class<BitcoindRpcClient> bitcoindClientClass;
    try {
      bitcoindClientClass = (Class<BitcoindRpcClient>) Class.forName(bitcoindClientClassName);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(bitcoindClientClassName + " not found", e);
    }

    // obtain an instance from the two argument constructor
    final Constructor<BitcoindRpcClient> constructor;
    try {
      constructor = bitcoindClientClass.getDeclaredConstructor(Config.class);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Could not find constructor with single argument of type "
          + Config.class.getName() + " in " + bitcoindClientClassName);
    }

    try {
      this.client = constructor.newInstance(config.getConfig(PACIOFS_BITCOIND_CLIENT_KEY));
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException("Could not instantiate " + bitcoindClientClassName, e);
    }

    // shut down the blockchain before the actor
    CoordinatedShutdown.get(this.getContext().getSystem())
        .addJvmShutdownHook(() -> this.client.stop());
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();

    // TODO if this fails, should we fail the entire actor system?
    // warm up the client
    BitcoindRpcClient.BlockChainInfo bci = this.client.getBlockChainInfo();
    this.log.info("Connected to chain {}", bci.chain());
  }

  public static Props props() {
    return Props.create(Bitcoind.class, Bitcoind::new);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().build();
  }
}
