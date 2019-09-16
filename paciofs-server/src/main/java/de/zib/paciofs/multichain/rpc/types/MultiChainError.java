/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

public class MultiChainError {
  public static final int RPC_IN_WARMUP = -28;

  private int code;

  private String message;

  public MultiChainError() {}

  public int code() {
    return this.code;
  }

  public String message() {
    return this.message;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(MultiChainError.class.getSimpleName()).append("{");
    builder.append("code : ").append(this.code).append(", ");
    builder.append("message : ").append(this.message);
    builder.append("}");
    return builder.toString();
  }
}
