/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

import java.util.List;

public class MultiChainRequest {
  private final String id;

  private final String method;

  private final List<Object> params;

  public MultiChainRequest(String id, String method, List<Object> params) {
    this.id = id;
    this.method = method;
    this.params = params;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(MultiChainRequest.class.getSimpleName()).append("{");
    builder.append("id : ").append(this.id).append(", ");
    builder.append("method : ").append(this.method).append(", ");
    builder.append("params : [");
    for (int i = 0; i < this.params.size(); ++i) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(this.params.get(i));
    }
    builder.append("]");
    builder.append("}");
    return builder.toString();
  }
}
