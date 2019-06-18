/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class MultiChainData {
  private final ByteArrayInputStream bytesIn;
  private final DataInputStream dataIn;

  private final ByteArrayOutputStream bytesOut;
  private final DataOutputStream dataOut;

  public MultiChainData() {
    this.bytesIn = null;
    this.dataIn = null;
    this.bytesOut = new ByteArrayOutputStream();
    this.dataOut = new DataOutputStream(this.bytesOut);
  }

  public MultiChainData(byte[] data) {
    this.bytesIn = new ByteArrayInputStream(data);
    this.dataIn = new DataInputStream(this.bytesIn);
    this.bytesOut = null;
    this.dataOut = null;
  }

  public byte[] readByteArray() {
    try {
      final int length = this.dataIn.readInt();
      final byte[] bytes = new byte[length];
      final int n = this.dataIn.read(bytes);
      if (n < length) {
        throw new RuntimeException("Could not read entire array");
      }
      return bytes;
    } catch (IOException e) {
      // underlying buffer should never throw
      throw new AssertionError(e);
    }
  }

  public int readInt() {
    try {
      return this.dataIn.readInt();
    } catch (IOException e) {
      // underlying buffer should never throw
      throw new AssertionError(e);
    }
  }

  public long readLong() {
    try {
      return this.dataIn.readLong();
    } catch (IOException e) {
      // underlying buffer should never throw
      throw new AssertionError(e);
    }
  }

  public String readString() {
    try {
      return this.dataIn.readUTF();
    } catch (IOException e) {
      // underlying buffer should never throw
      throw new AssertionError(e);
    }
  }

  public void writeByteArray(byte[] b) {
    try {
      this.dataOut.writeInt(b.length);
      this.dataOut.write(b);
    } catch (IOException e) {
      // underlying buffer should never throw
      throw new AssertionError(e);
    }
  }

  public void writeInt(int i) {
    try {
      this.dataOut.writeInt(i);
    } catch (IOException e) {
      // underlying buffer should never throw
      throw new AssertionError(e);
    }
  }

  public void writeLong(long l) {
    try {
      this.dataOut.writeLong(l);
    } catch (IOException e) {
      // underlying buffer should never throw
      throw new AssertionError(e);
    }
  }

  public void writeString(String s) {
    try {
      this.dataOut.writeUTF(s);
    } catch (IOException e) {
      // underlying buffer should never throw
      throw new AssertionError(e);
    }
  }

  public byte[] toByteArray() {
    return this.bytesOut.toByteArray();
  }
}
