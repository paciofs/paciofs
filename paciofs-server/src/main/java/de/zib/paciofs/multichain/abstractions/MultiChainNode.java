/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.abstractions;

public class MultiChainNode implements Comparable<MultiChainNode> {
  private final String address;

  public MultiChainNode(String address) {
    this.address = address;
  }

  public String getAddress() {
    return this.address;
  }

  @Override
  public int compareTo(MultiChainNode o) {
    return this.address.compareTo(o.getAddress());
  }

  @Override
  public String toString() {
    return "MultiChainNode(" + this.address + ")";
  }
}
