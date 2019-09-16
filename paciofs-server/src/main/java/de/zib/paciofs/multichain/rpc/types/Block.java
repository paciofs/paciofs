/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

import com.google.gson.annotations.SerializedName;

public class Block {
  private String hash;

  private int height;

  private StringList tx;

  @SerializedName("previousblockhash") private String previousBlockHash;

  public Block() {}

  public String hash() {
    return this.hash;
  }

  public int height() {
    return this.height;
  }

  public StringList tx() {
    return this.tx;
  }

  public String previousBlockHash() {
    return this.previousBlockHash;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(Block.class.getSimpleName()).append("{");
    builder.append("hash : ").append(this.hash).append(", ");
    builder.append("height : ").append(this.height).append(", ");
    builder.append("tx: [");
    for (int i = 0; i < this.tx.size(); ++i) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(this.tx.get(i));
    }
    builder.append("], ");
    builder.append("previousBlockHash : ").append(this.previousBlockHash);
    builder.append("}");
    return builder.toString();
  }
}
