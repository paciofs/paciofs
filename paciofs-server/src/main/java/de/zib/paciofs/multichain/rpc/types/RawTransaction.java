/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;
import java.util.ArrayList;

public class RawTransaction {
  public static class Out {
    public static class ScriptPubKey {
      private String asm;

      private String hex;

      private int reqSigs;

      private String type;

      private StringList addresses;

      public ScriptPubKey() {}

      public String asm() {
        return this.asm;
      }

      public String hex() {
        return this.hex;
      }

      public int reqSigs() {
        return this.reqSigs;
      }

      public String type() {
        return this.type;
      }

      public StringList addresses() {
        return this.addresses;
      }

      @Override
      public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(ScriptPubKey.class.getSimpleName()).append("{");
        builder.append("asm : ").append(this.asm).append(", ");
        builder.append("hex : ").append(this.hex).append(", ");
        builder.append("reqSigs : ").append(this.reqSigs).append(", ");
        builder.append("type : ").append(this.type).append(", ");
        builder.append("addresses : ").append(this.addresses);
        builder.append("}");
        return builder.toString();
      }
    }

    private BigDecimal value;

    private int n;

    private ScriptPubKey scriptPubKey;

    public Out() {}

    public BigDecimal value() {
      return this.value;
    }

    public int n() {
      return this.n;
    }

    public ScriptPubKey scriptPubKey() {
      return this.scriptPubKey;
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append(Out.class.getSimpleName()).append("{");
      builder.append("value : ").append(this.value).append(", ");
      builder.append("n : ").append(this.n).append(", ");
      builder.append("scriptPubKey : ").append(this.scriptPubKey);
      builder.append("}");
      return builder.toString();
    }
  }

  public static class OutList extends ArrayList<Out> {
    public OutList() {}

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append(OutList.class.getSimpleName()).append("{");
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

  @SerializedName("txid") private String id;

  @SerializedName("vout") private OutList vOut;

  public RawTransaction() {}

  public String id() {
    return this.id;
  }

  public OutList vOut() {
    return this.vOut;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(RawTransaction.class.getSimpleName()).append("{");
    builder.append("id : ").append(this.id).append(", ");
    builder.append("vOut : ").append(this.vOut);
    builder.append("}");
    return builder.toString();
  }
}
