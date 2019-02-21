/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc;

import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URL;
import java.util.Map;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;

/**
 * Exposes some of MultiChain's custom commands.
 * @see <a href="https://www.multichain.com/developers/json-rpc-api/">JSON RPC API</a>
 */
public class MultiChainJsonRpcClient extends BitcoinJSONRPCClient implements MultiChainRpcClient {
  public MultiChainJsonRpcClient() {
    super();
  }

  public MultiChainJsonRpcClient(URL url) {
    super(url);
  }

  @Override
  public String createStream(String name, boolean open) {
    return (String) this.query("create", "stream", name, open);
  }

  @Override
  public Info getInfo() {
    return new InfoMapWrapper((Map<String, ?>) this.query("getinfo"));
  }

  @Override
  public void subscribe(String streamRef) {
    this.query("subscribe", streamRef);
  }

  private static class InfoMapWrapper extends MapWrapper implements Info, Serializable {
    private static final long serialVersionUID = 2509558745656609453L;

    private InfoMapWrapper(Map<String, ?> map) {
      super(map);
    }

    @Override
    public String version() {
      return this.mapStr("version");
    }

    @Override
    public int nodeVersion() {
      return this.mapInt("nodeversion");
    }

    @Override
    public int protocolVersion() {
      return this.mapInt("protocolversion");
    }

    @Override
    public String chainName() {
      return this.mapStr("chainname");
    }

    @Override
    public String description() {
      return this.mapStr("description");
    }

    @Override
    public String protocol() {
      return this.mapStr("protocol");
    }

    @Override
    public short port() {
      return this.mapShort("port");
    }

    @Override
    public long setupBlocks() {
      return this.mapLong("setupblocks");
    }

    @Override
    public String nodeAddress() {
      return this.mapStr("nodeaddress");
    }

    @Override
    public String burnAddress() {
      return this.mapStr("burnaddress");
    }

    @Override
    public boolean incomingPaused() {
      return this.mapBool("incomingpaused");
    }

    @Override
    public boolean miningPaused() {
      return this.mapBool("miningpaused");
    }

    @Override
    public boolean offChainPaused() {
      return this.mapBool("offchainpaused");
    }

    @Override
    public int walletVersion() {
      return this.mapInt("walletversion");
    }

    @Override
    public long balance() {
      return this.mapLong("balance");
    }

    @Override
    public int walletDbVersion() {
      return this.mapInt("walletdbversion");
    }

    @Override
    public boolean reindex() {
      return this.mapBool("reindex");
    }

    @Override
    public int blocks() {
      return this.mapInt("blocks");
    }

    @Override
    public long timeOffset() {
      return this.mapLong("timeoffset");
    }

    @Override
    public int connections() {
      return this.mapInt("connections");
    }

    @Override
    public String proxy() {
      return this.mapStr("proxy");
    }

    @Override
    public BigDecimal difficulty() {
      return this.mapBigDecimal("difficulty");
    }

    @Override
    public boolean testnet() {
      return this.mapBool("testnet");
    }

    @Override
    public long keyPoolOldest() {
      return this.mapLong("keypoololdest");
    }

    @Override
    public int keyPoolSize() {
      return this.mapInt("keypoolsize");
    }

    @Override
    public long payTxFee() {
      return this.mapLong("paytxfee");
    }

    @Override
    public long relayFee() {
      return this.mapLong("relayfee");
    }

    @Override
    public String errors() {
      return this.mapStr("errors");
    }
  }
}
