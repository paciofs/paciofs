/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.blockchain;

import akka.event.LoggingAdapter;
import com.typesafe.config.Config;

public interface BlockchainService {
  public void configure(Config config, LoggingAdapter log);

  public void start();

  public void stop();
}
