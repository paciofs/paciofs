/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

import java.math.BigDecimal;

public class TransactionOutput {
  private String address;

  private BigDecimal amount;

  private String data;

  public TransactionOutput(String address, BigDecimal amount, String data) {
    this.address = address;
    this.amount = amount;
    this.data = data;
  }

  public String address() {
    return this.address;
  }

  public BigDecimal amount() {
    return this.amount;
  }

  public String data() {
    return this.data;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(TransactionOutput.class.getSimpleName()).append("{");
    builder.append(this.address).append(" : ").append(this.amount);
    if (this.data != null) {
      builder.append(", data : ").append(this.data);
    }
    builder.append("}");
    return builder.toString();
  }
}
