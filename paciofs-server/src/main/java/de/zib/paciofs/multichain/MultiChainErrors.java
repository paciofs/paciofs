/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

// https://www.multichain.com/developers/api-errors/
public class MultiChainErrors {
  // JSON-RPC processing errors
  public static final int RPC_INVALID_REQUEST = -32600;
  public static final int RPC_METHOD_NOT_FOUND = -32601;
  public static final int RPC_INVALID_PARAMS = -32602;
  public static final int RPC_INTERNAL_ERROR = -32603;
  public static final int RPC_PARSE_ERROR = -32700;

  // General application errors (from Bitcoin Core)
  // use wf.bitcoin.javabitcoindrpcclient.BitcoinRPCErrorCode

  // General application errors (MultiChain specific)
  public static final int RPC_NOT_ALLOWED = -701;
  public static final int RPC_NOT_SUPPORTED = -702;
  public static final int RPC_NOT_SUBSCRIBED = -703;
  public static final int RPC_INSUFFICIENT_PERMISSIONS = -704;
  public static final int RPC_DUPLICATE_NAME = -705;
  public static final int RPC_UNCONFIRMED_ENTITY = -706;
  public static final int RPC_EXCHANGE_ERROR = -707;
  public static final int RPC_ENTITY_NOT_FOUND = -708;
  public static final int RPC_WALLET_ADDRESS_NOT_FOUND = -709;
  public static final int RPC_TX_NOT_FOUND = -710;
  public static final int RPC_BLOCK_NOT_FOUND = -711;
  public static final int RPC_OUTPUT_NOT_FOUND = -712;
  public static final int RPC_OUTPUT_NOT_DATA = -713;
  public static final int RPC_INPUTS_NOT_MINE = -714;
  public static final int RPC_WALLET_OUTPUT_NOT_FOUND = -715;
  public static final int RPC_WALLET_NO_UNSPENT_OUTPUTS = -716;
  public static final int RPC_GENERAL_FILE_ERROR = -717;
  public static final int RPC_UPGRADE_REQUIRED = -718;

  // Peer-to-peer client errors
  public static final int RPC_CLIENT_NOT_CONNECTED = -9;
  public static final int RPC_CLIENT_IN_INITIAL_DOWNLOAD = -10;
  public static final int RPC_CLIENT_NODE_ALREADY_ADDED = -23;
  public static final int RPC_CLIENT_NODE_NOT_ADDED = -24;

  // Wallet errors
  public static final int RPC_WALLET_ERROR = -4;
  public static final int RPC_WALLET_INSUFFICIENT_FUNDS = -6;
  public static final int RPC_WALLET_INVALID_ACCOUNT_NAME = -11;
  public static final int RPC_WALLET_KEYPOOL_RAN_OUT = -12;
  public static final int RPC_WALLET_UNLOCK_NEEDED = -13;
  public static final int RPC_WALLET_PASSPHRASE_INCORRECT = -14;
  public static final int RPC_WALLET_WRONG_ENC_STATE = -15;
  public static final int RPC_WALLET_ENCRYPTION_FAILED = -16;

  private MultiChainErrors() {}
}
