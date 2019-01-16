/*
 * Copyright (c) 2010, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.blockchain.multichain;

import com.typesafe.config.Config;
import de.zib.paciofs.blockchain.BlockchainService;

public class MultiChainService implements BlockchainService {

  private static final String MULTICHAIN_CONFIG_KEY =
      "paciofs.blockchain-service.multichain";

  private Config config;

  @Override
  public void configure(Config config) {
    this.config = config.getConfig(MULTICHAIN_CONFIG_KEY);
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}
}
