/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc.types;

public class MultiChainException extends RuntimeException {
  private final MultiChainError error;

  public MultiChainException(MultiChainError error) {
    super(String.valueOf(error));
    this.error = error;
  }

  public MultiChainException(String message) {
    super(message);
    this.error = null;
  }

  public MultiChainException(String message, Throwable cause) {
    super(message, cause);
    this.error = null;
  }

  public MultiChainError getError() {
    return this.error;
  }
}
