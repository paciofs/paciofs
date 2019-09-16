/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

public class MultiChainResponse<T> {
  private String id;

  private MultiChainError error;

  private T result;

  public MultiChainResponse() {}

  public String id() {
    return this.id;
  }

  public MultiChainError error() {
    return this.error;
  }

  public T result() {
    return this.result;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(MultiChainResponse.class.getSimpleName()).append("{");
    builder.append("id : ").append(this.id).append(", ");
    builder.append("error : ").append(this.error).append(", ");
    builder.append("result : ").append(this.result);
    builder.append("}");
    return builder.toString();
  }
}
