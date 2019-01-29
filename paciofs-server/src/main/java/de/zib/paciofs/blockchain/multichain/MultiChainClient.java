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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCError;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCException;
import wf.bitcoin.javabitcoindrpcclient.GenericRpcException;

public class MultiChainClient extends MultiChainJsonRpcClient {
  private enum LifecyclePhase { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

  private static final Logger LOG = LoggerFactory.getLogger(MultiChainClient.class);

  private static final Map<LifecyclePhase, Set<LifecyclePhase>> LIFECYCLE;

  private final Config config;

  private final MultiChaind multiChaind;

  private LifecyclePhase lifecyclePhase;

  private final Object lifecyclePhaseTransition;

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
      this.lifecyclePhase = LifecyclePhase.STOPPED;
      this.lifecyclePhaseTransition = new Object();
    } else {
      // assume all is well if we connect to a remote MultiChain
      this.multiChaind = null;
      this.lifecyclePhase = LifecyclePhase.RUNNING;
      this.lifecyclePhaseTransition = null;
    }
  }

  static {
    final Map<LifecyclePhase, Set<LifecyclePhase>> lifecycle = new HashMap<>();
    lifecycle.put(LifecyclePhase.STOPPED, lifecyclePhases(LifecyclePhase.STARTING));
    lifecycle.put(
        LifecyclePhase.STARTING, lifecyclePhases(LifecyclePhase.RUNNING, LifecyclePhase.FAILED));
    lifecycle.put(LifecyclePhase.RUNNING, lifecyclePhases(LifecyclePhase.STOPPING));
    lifecycle.put(LifecyclePhase.STOPPING, lifecyclePhases(LifecyclePhase.STOPPED));
    lifecycle.put(LifecyclePhase.FAILED, lifecyclePhases());
    LIFECYCLE = Collections.unmodifiableMap(lifecycle);
  }

  private static Set<LifecyclePhase> lifecyclePhases(LifecyclePhase... l) {
    final Set<LifecyclePhase> s = new HashSet<>();
    Collections.addAll(s, l);
    return Collections.unmodifiableSet(s);
  }

  @Override
  public Object query(String method, Object... o) throws GenericRpcException {
    this.ensureRunning();

    // logging is potentially expensive here
    if (LOG.isTraceEnabled()) {
      LOG.trace("Query: {}{}", method, o);
      final Object result = super.query(method, o);
      LOG.trace("Result: {}", result);
      return result;
    }

    // TODO surround with try-catch and maybe switch to FAILED if we get errors
    return super.query(method, o);
  }

  @Override
  public void addNode(String node, String command) throws GenericRpcException {
    final String nodeWithPort = node + ":" + this.config.getInt(MultiChainOptions.PORT_KEY);

    // https://www.multichain.com/developers/json-rpc-api/
    // The command parameter should be one of add (to manually queue a node for the next available
    // slot), remove (to remove a node), or onetry (to immediately connect to a node even if a slot
    // is not available).
    LOG.trace("{}'ing {}", command, nodeWithPort);
    super.addNode(nodeWithPort, command);
    LOG.trace("{}'ed {}", command, nodeWithPort);
  }

  // manages the transition from RUNNING -> STOPPING -> STOPPED in a blocking fashion
  @Override
  public void stop() {
    if (this.lifecyclePhase == LifecyclePhase.STOPPED || this.multiChaind == null) {
      // all is well, or this is a remote chain which we will not stop
      return;
    }

    synchronized (this.lifecyclePhaseTransition) {
      // check that we can transition to the STOPPING phase (also fails if we are STOPPED already)
      if (this.checkedLifecyclePhaseTransition(LifecyclePhase.STOPPING)) {
        // seems more reliable: we get an exit code, and the RPC stop request fails since the
        // server does not answer anymore (since it is shutting down)
        this.multiChaind.terminate();

        // done
        this.forcedLifecyclePhaseTransition(LifecyclePhase.STOPPED);
      }
    }
  }

  // manages the transition from STOPPED -> STARTING -> RUNNING in a blocking fashion
  private void ensureRunning() {
    if (this.lifecyclePhase == LifecyclePhase.RUNNING) {
      // all is well
      return;
    }

    synchronized (this.lifecyclePhaseTransition) {
      // check that we can transition to the STARTING phase (also fails if we are RUNNING already)
      // this check also fails if we are STARTING, which we might encounter since the thread
      // performing the start holds the lock and recurses into ensureRunning() during the
      // getBlockChainInfo() query (see below)
      if (this.checkedLifecyclePhaseTransition(LifecyclePhase.STARTING)) {
        // wait until the service is up
        this.multiChaind.start();

        // wait at most backoff * (2^maxRetries - 1) milliseconds
        // e.g. 50 * (2^10 -1) = 51150 milliseconds
        long backoff = this.config.getLong(MultiChainOptions.BACKOFF_MILLISECONDS);
        final int maxRetries = this.config.getInt(MultiChainOptions.BACKOFF_RETRIES);
        int failedRetries = 0;

        // try and get the blockchain info at most a number of maxRetries times
        BlockChainInfo bci = null;
        for (; failedRetries < maxRetries; ++failedRetries) {
          try {
            // causes one recursion step via query(), however this step will fail the above
            // checkedLifecyclePhaseTransition to STARTING and therefore return early
            bci = this.getBlockChainInfo();
            break;
          } catch (BitcoinRPCException rpcException) {
            final BitcoinRPCError rpcError = rpcException.getRPCError();

            // check what has gone wrong
            if (rpcError != null && rpcError.getCode() == RPC_IN_WARMUP) {
              // keep waiting, multichaind is at work and will be with us soon
              LOG.debug(
                  "Waiting {} ms, multichaind is warming up ({})", backoff, rpcError.getMessage());
              --failedRetries;
            } else if (!this.multiChaind.isRunning()) {
              // multichaind has crashed
              this.forcedLifecyclePhaseTransition(LifecyclePhase.FAILED);
              throw new RuntimeException("multichaind stopped running");
            } else {
              // we do not know yet what is wrong
              LOG.debug("Waiting {} ms, multichaind has not started yet ({})", backoff,
                  rpcException.getMessage());
              LOG.debug(Markers.EXCEPTION, "multichaind has not started yet", rpcException);
            }

            // exponential backoff so we do not annoy multichaind too much
            try {
              Thread.sleep(backoff);
              backoff *= 2;
            } catch (InterruptedException e) {
              LOG.debug("Interrupted while waiting for multichaind to start: {}", e.getMessage());
              LOG.debug(Markers.EXCEPTION, "Interrupted while waiting for multichaind to start", e);
            }
          }
        }

        if (bci == null) {
          this.forcedLifecyclePhaseTransition(LifecyclePhase.FAILED);
          throw new RuntimeException("multichaind not up after " + failedRetries + " retries");
        }

        // done
        LOG.debug(
            "multichaind up after {} retries, connected to chain {}", failedRetries, bci.chain());
        this.forcedLifecyclePhaseTransition(LifecyclePhase.RUNNING);
      }
    }
  }

  // not thread-safe
  private boolean checkedLifecyclePhaseTransition(LifecyclePhase to) {
    if (LIFECYCLE.get(this.lifecyclePhase).contains(to)) {
      this.lifecyclePhase = to;
      return true;
    }

    LOG.debug("Illegal lifecycle phase transition: {} -> {}", this.lifecyclePhase, to);
    return false;
  }

  // not thread-safe
  private void forcedLifecyclePhaseTransition(LifecyclePhase to) {
    if (!this.checkedLifecyclePhaseTransition(to)) {
      throw new IllegalStateException("Expected to be able to switch to " + to);
    }
  }
}
