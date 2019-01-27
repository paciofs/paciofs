/*
 * Copyright (c) 2010, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.blockchain.multichain;

import com.typesafe.config.Config;
import de.zib.paciofs.logging.Markers;
import java.net.MalformedURLException;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCError;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCException;
import wf.bitcoin.javabitcoindrpcclient.GenericRpcException;

public class MultiChainClient extends BitcoinJSONRPCClient {
  private enum State { STOPPED, STARTING, RUNNING, STOPPING }

  private static final Logger LOG = LoggerFactory.getLogger(MultiChainClient.class);

  private final Config config;

  private final MultiChaind multiChaind;

  private State state;

  /**
   * Creates the MultiChain client.
   * @param config configuration containing a mapping of the multichaind options (without leading
   *     dashes)
   * @throws MalformedURLException if the URL constructed from the configuration is invalid
   */
  public MultiChainClient(Config config) throws MalformedURLException {
    super(new URL("http://" + config.getString(MultiChainOptions.RPC_USER_KEY) + ":"
        + config.getString(MultiChainOptions.RPC_PASSWORD_KEY) + "@"
        + config.getString(MultiChainOptions.RPC_CONNECT_KEY) + ":"
        + config.getInt(MultiChainOptions.RPC_PORT_KEY)));
    this.config = config;

    // start MultiChain locally if localhost is the target connect
    if ("localhost".equals(this.config.getString(MultiChainOptions.RPC_CONNECT_KEY))) {
      this.multiChaind = new MultiChaind(this.config);
      this.state = State.STOPPED;
    } else {
      // assume all is well if we connect to a remote MultiChain
      this.multiChaind = null;
      this.state = State.RUNNING;
    }
  }

  @Override
  public Object query(String method, Object... o) throws GenericRpcException {
    this.ensureRunning();
    return super.query(method, o);
  }

  @Override
  public void addNode(String node, String command) throws GenericRpcException {
    super.addNode(node + ":" + this.config.getInt(MultiChainOptions.PORT_KEY), command);
  }

  // manages the transition from RUNNING -> STOPPING -> STOPPED
  @Override
  public void stop() {
    if (this.state != State.RUNNING) {
      // if chain is STARTING: let the chain start first before stopping
      // if chain is STOPPING: some other thread is already stopping the chain
      // if chain is STOPPED: well, we are good then
    } else {
      // have exactly one thread do the stopping
      synchronized (this) {
        if (this.state == State.RUNNING) {
          this.state = State.STOPPING;
        } else {
          return;
        }
      }

      if (this.multiChaind != null) {
        // seems more reliable: we get an exit code, and the RPC stop request fails since the server
        // does not answer anymore (since it is shutting down)
        this.multiChaind.terminate();
      } else {
        // TODO do we shut down remote chains? this should be prohibited by proper authorization
        super.stop();
      }

      // done
      this.state = State.STOPPED;
    }
  }

  // manages the transition from STOPPED -> STARTING -> RUNNING
  private void ensureRunning() {
    if (this.state != State.STOPPED) {
      // if chain is STARTING: nothing we can do except wait (see below)
      // if chain is RUNNING: well, we are good then
      // if chain is STOPPING: let the chain stop first before starting
    } else {
      // have exactly one thread do the starting
      synchronized (this) {
        if (this.state == State.STOPPED) {
          this.state = State.STARTING;
        } else {
          return;
        }
      }

      // wait until the service is up
      final long startWait = System.currentTimeMillis();
      this.multiChaind.start();

      // the code multichain responds with while warming up
      final int multiChaindWarmupCode = this.config.getInt(MultiChainOptions.WARMUP_CODE_KEY);

      // wait at most backoff * (2^maxRetries - 1) milliseconds
      // e.g. 50 * (2^10 -1) = 51150 milliseconds
      long backoff = this.config.getLong(MultiChainOptions.BACKOFF_MILLISECONDS);
      final int maxRetries = this.config.getInt(MultiChainOptions.BACKOFF_RETRIES);
      int failedRetries = 0;
      BitcoinRPCException lastCaught = null;
      for (; failedRetries < maxRetries; ++failedRetries) {
        try {
          final BlockChainInfo bci = this.getBlockChainInfo();
          LOG.debug("Connected to chain {}", bci.chain());
          break;
        } catch (BitcoinRPCException rpcException) {
          final BitcoinRPCError rpcError = rpcException.getRPCError();

          if (rpcError != null && rpcError.getCode() == multiChaindWarmupCode) {
            LOG.debug(
                "Waiting {} ms, multichaind is warming up ({})", backoff, rpcError.getMessage());

            // keep waiting, multichaind is at work and will be with us soon
            --failedRetries;
          } else {
            // multichaind might have crashed
            if (!this.multiChaind.isRunning()) {
              this.state = State.STOPPED;
              throw new RuntimeException("multichaind stopped running");
            } else {
              lastCaught = rpcException;
              LOG.debug("Waiting {} ms, multichaind has not started yet ({})", backoff,
                  rpcException.getMessage());
              LOG.debug(Markers.EXCEPTION, "multichaind has not started yet", rpcException);
            }
          }

          // exponential backoff so we do not annoy multichaind too much
          try {
            Thread.sleep(backoff);
            backoff *= 2;
          } catch (InterruptedException e) {
            if (LOG.isDebugEnabled(Markers.EXCEPTION)) {
              LOG.debug(Markers.EXCEPTION, "Interrupted while waiting for multichaind to start", e);
            } else {
              LOG.debug("Interrupted while waiting for multichaind to start: {}", e.getMessage());
            }
          }
        }
      }

      final long waitTime = System.currentTimeMillis() - startWait;
      if (failedRetries == maxRetries) {
        throw new RuntimeException("multichaind not up after " + waitTime + " ms", lastCaught);
      } else {
        LOG.debug("multichaind up after {} ms", waitTime);
      }

      // done
      this.state = State.RUNNING;
    }
  }
}
