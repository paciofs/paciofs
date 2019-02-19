/*
 * Copyright (c) 2010, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

import com.typesafe.config.Config;
import de.zib.paciofs.logging.Markers;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
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

  private final MultiChainDaemon multiChainDaemon;

  private String localhostName;
  private String localhostAddress;

  private LifecyclePhase lifecyclePhase;

  private final Object lifecyclePhaseTransition;

  /**
   * Creates the MultiChain client.
   * @param config configuration containing a mapping of the multichaind options (without leading
   *     dashes)
   * @throws MalformedURLException if the URL constructed from the configuration is invalid
   */
  public MultiChainClient(Config config) throws MalformedURLException {
    this.config = config;

    // start MultiChain locally if localhost is the target connect
    if ("localhost".equals(this.config.getString(MultiChainOptions.RPC_CONNECT_KEY))) {
      this.multiChainDaemon = new MultiChainDaemon(this.config);
      this.lifecyclePhase = LifecyclePhase.STOPPED;
      this.lifecyclePhaseTransition = new Object();

      // we need to start multichaind first and obtain the user info from it
    } else {
      // assume all is well if we connect to a remote MultiChain
      this.multiChainDaemon = null;
      this.lifecyclePhase = LifecyclePhase.RUNNING;
      this.lifecyclePhaseTransition = null;

      // set target URL to configured values
      this.setUrl(this.getProtocol(), config.getString(MultiChainOptions.RPC_USER_KEY),
          config.getString(MultiChainOptions.RPC_PASSWORD_KEY),
          config.getString(MultiChainOptions.RPC_CONNECT_KEY),
          config.getInt(MultiChainOptions.RPC_PORT_KEY));
    }

    try {
      final InetAddress localhost = InetAddress.getLocalHost();
      this.localhostName = localhost.getHostName();
      this.localhostAddress = localhost.getHostAddress();
    } catch (UnknownHostException e) {
      LOG.warn("Could not get localhost: {}" + e.getMessage());
      LOG.warn(Markers.EXCEPTION, "Could not get localhost", e);
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

    try {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Query: {}{}", method, o);
        final Object result = super.query(method, o);
        LOG.trace("Result: {}", result);
        return result;
      }

      return super.query(method, o);
    } catch (GenericRpcException e) {
      // multichaind has died, switch to FAILED
      if (this.multiChainDaemon != null && !this.multiChainDaemon.isRunning()) {
        synchronized (this.lifecyclePhaseTransition) {
          LOG.error("multichaind has stopped running: {}", e.getMessage());
          LOG.error(Markers.EXCEPTION, "multichaind has stopped running", e);
          if (this.checkedLifecyclePhaseTransition(LifecyclePhase.FAILED)) {
            this.multiChainDaemon.waitForTermination();
          }
        }
      }

      throw e;
    }
  }

  @Override
  public void addNode(String node, String command) throws GenericRpcException {
    if (node.equals(this.localhostName) || node.equals(this.localhostAddress)) {
      LOG.debug("Not {}'ing {} because it is this node", command, node);
      return;
    }

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
    if (this.lifecyclePhase == LifecyclePhase.STOPPED || this.multiChainDaemon == null) {
      // all is well, or this is a remote chain which we will not stop
      return;
    }

    synchronized (this.lifecyclePhaseTransition) {
      // check that we can transition to the STOPPING phase (also fails if we are STOPPED already)
      if (this.checkedLifecyclePhaseTransition(LifecyclePhase.STOPPING)) {
        LOG.info("Stopping multichaind");

        try {
          // gracefully terminate by sending the RPC command to stop
          super.stop();
        } catch (BitcoinRPCException e) {
          LOG.debug("Expected exception during stop: {}", e.getMessage());
          LOG.debug(Markers.EXCEPTION, "Expected exception during stop", e);
        }

        // wait for multichaind to fully stop before setting the lifecycle phase
        this.multiChainDaemon.waitForTermination();

        // done
        LOG.info("Stopped multichaind");
        this.forcedLifecyclePhaseTransition(LifecyclePhase.STOPPED);
      }
    }
  }

  private String getProtocol() {
    return this.config.hasPath(MultiChainOptions.RPC_SSL_KEY) ? "https" : "http";
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
        this.multiChainDaemon.start();

        // now we have all the information of where to direct our RPC calls
        this.setUrl(this.getProtocol(),
            this.multiChainDaemon.getMultiChainConf().getString(MultiChainOptions.RPC_USER_KEY),
            this.multiChainDaemon.getMultiChainConf().getString(MultiChainOptions.RPC_PASSWORD_KEY),
            this.config.getString(MultiChainOptions.RPC_CONNECT_KEY),
            this.config.getInt(MultiChainOptions.RPC_PORT_KEY));

        // wait at most backoff * (2^maxRetries - 1) milliseconds
        // e.g. 50 * (2^10 -1) = 51150 milliseconds
        long backoff = this.config.getLong(MultiChainOptions.BACKOFF_MILLISECONDS_KEY);
        final int maxRetries = this.config.getInt(MultiChainOptions.BACKOFF_RETRIES_KEY);
        int failedRetries = 0;

        // try and get the multichain info at most a number of maxRetries times
        BlockChainInfo bci = null;
        for (; failedRetries < maxRetries; ++failedRetries) {
          try {
            // causes one recursion step via query(), however this step will fail the above
            // checkedLifecyclePhaseTransition to STARTING and therefore return early
            bci = this.getBlockChainInfo();
            break;
          } catch (BitcoinRPCException rpcException) {
            final BitcoinRPCError rpcError = rpcException.getRPCError();
            if (rpcError != null && rpcError.getCode() == RPC_IN_WARMUP) {
              // keep waiting, multichaind is at work and will be with us soon
              LOG.debug(
                  "Waiting {} ms, multichaind is warming up ({})", backoff, rpcError.getMessage());
              --failedRetries;
            } else if (!this.multiChainDaemon.isRunning()) {
              // multichaind has crashed
              this.forcedLifecyclePhaseTransition(LifecyclePhase.FAILED);
              throw new RuntimeException("multichaind stopped running");
            } else {
              // we do not know yet what is wrong, multichaind does not react to RPC calls
              LOG.debug("Waiting {} ms, multichaind has not started yet ({}: {})", backoff,
                  rpcException.getResponseCode(), rpcException.getMessage());
              LOG.debug(Markers.EXCEPTION, "multichaind has not started yet", rpcException);
            }

            // exponential backoff so we do not annoy multichaind too much
            try {
              // intentionally blocks all other threads
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
