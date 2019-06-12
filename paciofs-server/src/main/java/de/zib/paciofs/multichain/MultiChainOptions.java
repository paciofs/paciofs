/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

public class MultiChainOptions {
  public static final String BACKOFF_MILLISECONDS_KEY = "multichaind.backoff.milliseconds";
  public static final String BACKOFF_RETRIES_KEY = "multichaind.backoff.retries";

  public static final String CHAIN_NAME_KEY = "chain-name";

  public static final String DAEMON_OPTIONS_KEY = "multichaind.options";

  public static final String HOME_KEY = "home";

  public static final String PROTOCOL_VERSION_KEY = "protocol-version";

  public static final String SEED_NODE_KEY = "seed-node";

  private MultiChainOptions() {}
}
