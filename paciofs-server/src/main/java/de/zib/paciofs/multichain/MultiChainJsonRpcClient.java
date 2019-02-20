/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCException;
import wf.bitcoin.javabitcoindrpcclient.GenericRpcException;
import wf.bitcoin.krotjson.Base64Coder;

/**
 * Exposes some of MultiChain's custom commands.
 * @see <a href="https://www.multichain.com/developers/json-rpc-api/">JSON RPC API</a>
 */
public class MultiChainJsonRpcClient
    extends BitcoinJSONRPCClient implements MultiChainDaemonRpcClient {
  private static final int STREAM_BUFFER_SIZE = 1024;

  private URL url;

  private String auth;

  public MultiChainJsonRpcClient() {
    // always against testnet, because we never use this URL
    super(true);
  }

  // constructs the target URL and authentication information
  protected void setUrl(String protocol, String user, String password, String host, int port) {
    try {
      this.url = new URI(protocol, null, host, port, null, null, null).toURL();
    } catch (MalformedURLException | URISyntaxException ex) {
      throw new IllegalArgumentException(ex);
    }

    String userInfo = user;
    if (userInfo != null && password != null) {
      userInfo += ":" + password;
    }

    this.auth = userInfo == null
        ? null
        : String.valueOf(Base64Coder.encode(userInfo.getBytes(QUERY_CHARSET)));
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

  /*
   * from the 1.1.0 release commit
   * https://github.com/Polve/bitcoin-rpc-client/blob/a0005e146e863b4cb2610fb0257da25da0b70a11/src/main/java/wf/bitcoin/javabitcoindrpcclient/BitcoinJSONRPCClient.java#L209
   * fully override so we can add our authentication after (and not during) instantiation
   */

  @Override
  public Object query(String method, Object... o) throws GenericRpcException {
    try {
      final HttpURLConnection connection = (HttpURLConnection) this.url.openConnection();
      connection.setDoOutput(true);
      connection.setDoInput(true);

      if (connection instanceof HttpsURLConnection) {
        if (this.getHostnameVerifier() != null) {
          ((HttpsURLConnection) connection).setHostnameVerifier(this.getHostnameVerifier());
        }
        if (this.getSslSocketFactory() != null) {
          ((HttpsURLConnection) connection).setSSLSocketFactory(this.getSslSocketFactory());
        }
      }
      connection.setRequestProperty("Authorization", "Basic " + this.auth);

      final byte[] r = this.prepareRequest(method, o);
      connection.getOutputStream().write(r);
      connection.getOutputStream().close();

      final int responseCode = connection.getResponseCode();
      if (responseCode != HttpURLConnection.HTTP_OK) {
        final InputStream errorStream = connection.getErrorStream();
        throw new BitcoinRPCException(method, Arrays.deepToString(o), responseCode,
            connection.getResponseMessage(),
            errorStream == null ? null : new String(loadStream(errorStream, true), QUERY_CHARSET));
      }

      return this.loadResponse(connection.getInputStream(), "1", true);
    } catch (IOException ex) {
      throw new BitcoinRPCException(method, Arrays.deepToString(o), ex);
    }
  }

  private static byte[] loadStream(InputStream in, boolean close) throws IOException {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final byte[] buffer = new byte[STREAM_BUFFER_SIZE];
    for (;;) {
      final int read = in.read(buffer);
      if (read == -1) {
        break;
      } else if (read == 0) {
        throw new IOException("Read timed out");
      }

      baos.write(buffer, 0, read);
    }

    if (close) {
      in.close();
    }

    return baos.toByteArray();
  }
}
