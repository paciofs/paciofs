/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class UnspentTransactionOutput {
  @SerializedName("txid") private String txId;

  @SerializedName("vout") private int vOut;

  private String scriptPubKey;

  private BigDecimal amount;

  private boolean spendable;

  public UnspentTransactionOutput() {}

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

  public boolean spendable() {
    return this.spendable;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(UnspentTransactionOutput.class.getSimpleName()).append("{");
    builder.append("txId : ").append(this.txId).append(", ");
    builder.append("vOut : ").append(this.vOut).append(", ");
    builder.append("scriptPubKey : ").append(this.scriptPubKey).append(", ");
    builder.append("amount : ").append(this.amount).append(", ");
    builder.append("spendable : ").append(this.spendable);
    builder.append("}");
    return builder.toString();
  }
}
