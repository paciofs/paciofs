/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;

public class SignRawTransactionResult {
  public static class Error {
    @SerializedName("txid") private String txId;

    @SerializedName("vout") private int vOut;

    private String scriptSig;

    private long sequence;

    private String error;

    public Error() {}

    public String txId() {
      return this.txId;
    }

    public int vOut() {
      return this.vOut;
    }

    public String scriptSig() {
      return this.scriptSig;
    }

    public long sequence() {
      return this.sequence;
    }

    public String error() {
      return this.error;
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append(Error.class.getSimpleName()).append("{");
      builder.append("txId : ").append(this.txId).append(", ");
      builder.append("vOut : ").append(this.vOut).append(", ");
      builder.append("scriptSig : ").append(this.scriptSig).append(", ");
      builder.append("sequence : ").append(this.sequence).append(", ");
      builder.append("error : ").append(this.error);
      builder.append("}");
      return builder.toString();
    }
  }

  public static class ErrorList extends ArrayList<Error> {
    public ErrorList() {}

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append(ErrorList.class.getSimpleName()).append("{");
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

  private String hex;

  private boolean complete;

  private ErrorList errors;

  public SignRawTransactionResult() {}

  public String hex() {
    return this.hex;
  }

  public boolean complete() {
    return this.complete;
  }

  public ErrorList errors() {
    return this.errors;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(SignRawTransactionResult.class.getSimpleName()).append("{");
    builder.append("hex : ").append(this.hex).append(", ");
    builder.append("complete : ").append(this.complete).append(", ");
    builder.append("errors : ").append(this.errors);
    builder.append("}");
    return builder.toString();
  }
}
