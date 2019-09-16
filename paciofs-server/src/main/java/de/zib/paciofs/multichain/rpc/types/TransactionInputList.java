/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

import java.util.ArrayList;

public class TransactionInputList extends ArrayList<TransactionInput> {
  public TransactionInputList() {}

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(TransactionOutputList.class.getSimpleName()).append("{");
    builder.append("[");
    for (int i = 0; i < this.size(); ++i) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(this.get(i));
    }
    builder.append("]");
    builder.append("}");
    return builder.toString();
  }
}
