/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.blockchain;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;

public class PFSBlockchain extends AbstractActor {
  private final LoggingAdapter log;

  private final BlockchainService service;

  private static final String PACIOFS_BLOCKCHAIN_SERVICE_KEY =
      "paciofs.blockchain-service";

  private PFSBlockchain() {
    this.log = Logging.getLogger(this.getContext().system(), this);

    final Config config = this.getContext().system().settings().config();

    // may throw if key is empty or null
    final String blockchainServiceClassName =
        config.getString(PACIOFS_BLOCKCHAIN_SERVICE_KEY);

    // find the class implementing the blockchain service
    final Class<BlockchainService> blockchainServiceClass;
    try {
      blockchainServiceClass =
          (Class<BlockchainService>)Class.forName(blockchainServiceClassName);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(blockchainServiceClassName + " not found", e);
    }

    // obtain an instance from the nullary constructor
    try {
      this.service = blockchainServiceClass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new RuntimeException(
          "Could not instantiate " + blockchainServiceClassName, e);
    }

    // finally configure the service
    this.service.configure(config);
  }

  public static Props props() {
    return Props.create(PFSBlockchain.class, PFSBlockchain::new);
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();
    service.start();
  }

  @Override
  public void postStop() throws Exception {
    service.stop();
    super.postStop();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().build();
  }
}
