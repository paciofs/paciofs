/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class TransactionInput {
  @SerializedName("txid") private String txId;

  @SerializedName("vout") private int vOut;

  private String scriptPubKey;

  private BigDecimal amount;

  public TransactionInput() {}

  public TransactionInput(String txId, int vOut, String scriptPubKey, BigDecimal amount) {
    this.txId = txId;
    this.vOut = vOut;
    this.scriptPubKey = scriptPubKey;
    this.amount = amount;
  }

  public String txId() {
    return this.txId;
  }

  public int vOut() {
    return this.vOut;
  }

  public String scriptPubKey() {
    return this.scriptPubKey;
  }

  public BigDecimal amount() {
    return this.amount;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(TransactionInput.class.getSimpleName()).append("{");
    builder.append("txId : ").append(this.txId).append(", ");
    builder.append("vOut : ").append(this.vOut).append(", ");
    builder.append("scriptPubKey : ").append(this.scriptPubKey).append(", ");
    builder.append("amount : ").append(this.amount);
    builder.append("}");
    return builder.toString();
  }
}
