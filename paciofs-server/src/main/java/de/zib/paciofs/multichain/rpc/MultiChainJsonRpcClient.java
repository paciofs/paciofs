/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain.rpc;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import de.zib.paciofs.multichain.rpc.types.Block;
import de.zib.paciofs.multichain.rpc.types.BlockChainInfo;
import de.zib.paciofs.multichain.rpc.types.MultiChainException;
import de.zib.paciofs.multichain.rpc.types.MultiChainRequest;
import de.zib.paciofs.multichain.rpc.types.MultiChainResponse;
import de.zib.paciofs.multichain.rpc.types.RawTransaction;
import de.zib.paciofs.multichain.rpc.types.SignRawTransactionResult;
import de.zib.paciofs.multichain.rpc.types.TransactionInputList;
import de.zib.paciofs.multichain.rpc.types.TransactionOutput;
import de.zib.paciofs.multichain.rpc.types.TransactionOutputList;
import de.zib.paciofs.multichain.rpc.types.UnspentTransactionOutputList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.text.CharacterPredicates;
import org.apache.commons.text.RandomStringGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exposes some of MultiChain's custom commands.
 * @see <a href="https://www.multichain.com/developers/json-rpc-api/">JSON RPC API</a>
 */
public class MultiChainJsonRpcClient implements MultiChainClient {
  private static final Charset QUERY_CHARSET = Charset.forName("ISO8859-1");

  private static final ConcurrentHashMap<Class, Type> TYPES = new ConcurrentHashMap<>();

  private static final Logger LOG = LoggerFactory.getLogger(MultiChainJsonRpcClient.class);

  private final URL url;

  private String auth;

  private final Gson gson;

  private final RandomStringGenerator requestIdGenerator;

  public MultiChainJsonRpcClient(URL url) {
    this.url = url;
    this.gson = new Gson();
    this.requestIdGenerator = new RandomStringGenerator.Builder()
                                  .withinRange('0', 'z')
                                  .filteredBy(CharacterPredicates.ASCII_ALPHA_NUMERALS)
                                  .build();
  }

  protected <T> T query(String method, List<Object> params, Type resultType)
      throws MultiChainException {
    final HttpURLConnection connection;
    try {
      // url starts with http:// so the cast is safe
      connection = (HttpURLConnection) this.url.openConnection();
    } catch (IOException e) {
      throw new MultiChainException("Could not open connection", e);
    }

    connection.setDoInput(true);
    connection.setDoOutput(true);
    connection.setRequestProperty("Authorization", "Basic " + this.auth);

    final String requestId = this.requestIdGenerator.generate(16);
    final MultiChainRequest request = new MultiChainRequest(requestId, method, params);
    final String requestString;
    try {
      requestString = this.gson.toJson(request);
    } catch (JsonParseException e) {
      throw new MultiChainException("Could not serialize request to Json", e);
    }

    try {
      LOG.trace("MultiChain RPC request: {}", requestString);
      connection.getOutputStream().write(requestString.getBytes(QUERY_CHARSET));
      connection.getOutputStream().close();
    } catch (IOException e) {
      throw new MultiChainException("Could not write to stream", e);
    }

    final int responseCode;
    try {
      responseCode = connection.getResponseCode();
    } catch (IOException e) {
      throw new MultiChainException("Could not get response", e);
    }

    if (responseCode == HttpURLConnection.HTTP_OK) {
      final InputStream stream;
      final ByteArrayOutputStream traceStream;
      try {
        if (LOG.isTraceEnabled()) {
          traceStream = new ByteArrayOutputStream();
          stream = new TeeInputStream(connection.getInputStream(), traceStream);
        } else {
          traceStream = null;
          stream = connection.getInputStream();
        }
      } catch (IOException e) {
        throw new MultiChainException("Could not open response stream", e);
      }

      final JsonReader reader =
          this.gson.newJsonReader(new InputStreamReader(stream, QUERY_CHARSET));
      final MultiChainResponse<T> response;
      try {
        response = this.gson.fromJson(reader, resultType);
      } catch (JsonParseException e) {
        throw new MultiChainException("Could not deserialize response from Json", e);
      }

      if (LOG.isTraceEnabled()) {
        try {
          LOG.trace("MultiChain RPC response: {}", traceStream.toString(QUERY_CHARSET.name()));
        } catch (UnsupportedEncodingException e) {
          LOG.warn("Could not decode RPC response", e);
        }
      }

      if (!requestId.equals(response.id())) {
        throw new MultiChainException(
            "Response ID " + response.id() + " does not match request ID " + requestId);
      }

      if (response.error() != null) {
        throw new MultiChainException("MultiChain error (" + response.error() + ")");
      }

      return response.result();
    } else {
      final InputStream stream = connection.getErrorStream();
      final JsonReader reader =
          this.gson.newJsonReader(new InputStreamReader(stream, QUERY_CHARSET));
      final MultiChainResponse<T> response;
      try {
        response = this.gson.fromJson(reader, resultType);
      } catch (JsonParseException e) {
        throw new MultiChainException("Could not deserialize response from Json", e);
      }

      throw new MultiChainException(response.error());
    }
  }

  protected void setAuth(String auth) {
    this.auth = auth == null
        ? null
        : String.valueOf(Base64.encodeBase64String(auth.getBytes(QUERY_CHARSET)));
  }

  @Override
  public String createRawTransaction(TransactionInputList inputs, TransactionOutputList outputs) {
    return this.createRawTransaction(inputs, outputs, false);
  }

  @Override
  public String createRawTransaction(TransactionInputList inputs, TransactionOutputList outputs,
      boolean signAndSend) throws MultiChainException {
    final List<Object> params = new ArrayList<>();
    params.add(inputs);

    // reorder outputs with regard to data for MultiChain
    final Map<String, Object> outputsWithoutData = new HashMap<>();
    final List<String> data = new ArrayList<>();
    for (TransactionOutput output : outputs) {
      outputsWithoutData.put(output.address(), output.amount());
      if (output.data() != null) {
        data.add(output.data());
      }
    }
    params.add(outputsWithoutData);
    params.add(data);
    if (signAndSend) {
      params.add("send");
    }

    return this.<String>query("createrawtransaction", params,
        TYPES.computeIfAbsent(
            String.class, c -> new TypeToken<MultiChainResponse<String>>() {}.getType()));
  }

  @Override
  public RawTransaction decodeRawTransaction(String transactionHex) {
    final List<Object> params = new ArrayList<>();
    params.add(transactionHex);
    return this.<RawTransaction>query("decoderawtransaction", params,
        TYPES.computeIfAbsent(RawTransaction.class,
            c -> new TypeToken<MultiChainResponse<RawTransaction>>() {}.getType()));
  }

  @Override
  public String getBestBlockHash() {
    return this.<String>query("getbestblockhash", null,
        TYPES.computeIfAbsent(
            String.class, c -> new TypeToken<MultiChainResponse<String>>() {}.getType()));
  }

  @Override
  public Block getBlock(String blockHash) {
    final List<Object> params = new ArrayList<>();
    params.add(blockHash);
    params.add(1); // verbosity
    return this.<Block>query("getblock", params,
        TYPES.computeIfAbsent(
            Block.class, c -> new TypeToken<MultiChainResponse<Block>>() {}.getType()));
  }

  @Override
  public BlockChainInfo getBlockChainInfo() throws MultiChainException {
    return this.<BlockChainInfo>query("getblockchaininfo", null,
        TYPES.computeIfAbsent(BlockChainInfo.class,
            c -> new TypeToken<MultiChainResponse<BlockChainInfo>>() {}.getType()));
  }

  @Override
  public String getBlockHash(int height) {
    final List<Object> params = new ArrayList<>();
    params.add(height);
    return this.<String>query("getblockhash", params,
        TYPES.computeIfAbsent(
            String.class, c -> new TypeToken<MultiChainResponse<String>>() {}.getType()));
  }

  @Override
  public String getNewAddress() {
    return this.<String>query("getnewaddress", null,
        TYPES.computeIfAbsent(
            String.class, c -> new TypeToken<MultiChainResponse<String>>() {}.getType()));
  }

  @Override
  public String getRawChangeAddress() {
    return this.<String>query("getrawchangeaddress", null,
        TYPES.computeIfAbsent(
            String.class, c -> new TypeToken<MultiChainResponse<String>>() {}.getType()));
  }

  @Override
  public RawTransaction getRawTransaction(String id) {
    final List<Object> params = new ArrayList<>();
    params.add(id);
    params.add(true); // verbose
    return this.<RawTransaction>query("getrawtransaction", params,
        TYPES.computeIfAbsent(RawTransaction.class,
            c -> new TypeToken<MultiChainResponse<RawTransaction>>() {}.getType()));
  }

  @Override
  public UnspentTransactionOutputList listUnspent(int minimumConfirmations) {
    final List<Object> params = new ArrayList<>();
    params.add(minimumConfirmations);
    return this.<UnspentTransactionOutputList>query("listunspent", params,
        TYPES.computeIfAbsent(UnspentTransactionOutputList.class,
            c -> new TypeToken<MultiChainResponse<UnspentTransactionOutputList>>() {}.getType()));
  }

  @Override
  public String sendRawTransaction(String transactionHex) {
    final List<Object> params = new ArrayList<>();
    params.add(transactionHex);
    return this.<String>query("sendrawtransaction", params,
        TYPES.computeIfAbsent(
            String.class, c -> new TypeToken<MultiChainResponse<String>>() {}.getType()));
  }

  @Override
  public String signRawTransactionWithWallet(String transactionHex, TransactionInputList inputs) {
    final List<Object> params = new ArrayList<>();
    params.add(transactionHex);
    params.add(inputs);

    final SignRawTransactionResult result =
        this.<SignRawTransactionResult>query("signrawtransaction", params,
            TYPES.computeIfAbsent(SignRawTransactionResult.class,
                c -> new TypeToken<MultiChainResponse<SignRawTransactionResult>>() {}.getType()));

    if (result.complete()) {
      return result.hex();
    } else {
      throw new MultiChainException(String.valueOf(result.errors()));
    }
  }

  @Override
  public void stop() throws MultiChainException {
    this.query("stop", null,
        TYPES.computeIfAbsent(
            Void.class, c -> new TypeToken<MultiChainResponse<Void>>() {}.getType()));
  }
}
