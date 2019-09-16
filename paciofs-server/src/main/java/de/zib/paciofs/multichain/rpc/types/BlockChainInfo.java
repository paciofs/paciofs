/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

public class BlockChainInfo {
  private String chain;

  public BlockChainInfo() {}

  public String chain() {
    return this.chain;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(BlockChainInfo.class.getSimpleName()).append("{");
    builder.append("chain : ").append(this.chain);
    builder.append("}");
    return builder.toString();
  }
}
