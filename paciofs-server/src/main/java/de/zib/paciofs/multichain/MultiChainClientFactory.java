/*
 * Copyright (c) 2010, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import de.zib.paciofs.logging.Markers;
import de.zib.paciofs.multichain.rpc.MultiChainJsonRpcClient;
import de.zib.paciofs.multichain.rpc.types.BlockChainInfo;
import de.zib.paciofs.multichain.rpc.types.MultiChainError;
import de.zib.paciofs.multichain.rpc.types.MultiChainException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiChainClientFactory {
  /**
   * Phases the local MultiChain daemon can be in.
   */
  private enum LifecyclePhase { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

  /**
   * Client that starts a local MultiChain instance first.
   */
  private static class LocalClient extends MultiChainJsonRpcClient {
    private static final Logger LOG = LoggerFactory.getLogger(LocalClient.class);

    private final Config config;

    private String localhostName;

    private String localhostAddress;

    private final MultiChainDaemon multiChainDaemon;

    private LifecyclePhase multiChainLifecyclePhase;

    private final Object multiChainLifecyclePhaseTransition;

    private LocalClient(String protocol, Config config, MultiChainDaemon multiChainDaemon,
        LifecyclePhase lifecyclePhase, Object lifecyclePhaseTransition)
        throws MalformedURLException {
      // construct URL without credentials
      super(new URL(protocol + "://localhost:" + config.getInt(MultiChainOptions.RPC_PORT_KEY)));

      this.config = config;

      try {
        final InetAddress localhost = InetAddress.getLocalHost();
        this.localhostAddress = localhost.getHostAddress();
        this.localhostName = localhost.getHostName();
      } catch (UnknownHostException e) {
        this.localhostAddress = "";
        this.localhostName = "";
        LOG.warn("Could not get localhost: {}", e.getMessage());
        LOG.warn(Markers.EXCEPTION, "Could not get localhost", e);
      }

      this.multiChainDaemon = multiChainDaemon;
      this.multiChainLifecyclePhase = lifecyclePhase;
      this.multiChainLifecyclePhaseTransition = lifecyclePhaseTransition;
    }

    @Override
    protected <T> T query(String method, List<Object> params, Type resultType)
        throws MultiChainException {
      this.ensureRunning();

      try {
        return super.query(method, params, resultType);
      } catch (MultiChainException e) {
        // multichaind has died, switch to FAILED
        if (!this.multiChainDaemon.isRunning()) {
          synchronized (this.multiChainLifecyclePhaseTransition) {
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

    // manages the transition from RUNNING -> STOPPING -> STOPPED in a blocking fashion
    @Override
    public void stop() {
      if (this.multiChainLifecyclePhase == LifecyclePhase.STOPPED) {
        // all is well
        return;
      }

      synchronized (this.multiChainLifecyclePhaseTransition) {
        // check that we can transition to the STOPPING phase (also fails if we are STOPPED
        // already)
        if (this.checkedLifecyclePhaseTransition(LifecyclePhase.STOPPING)) {
          LOG.info("Stopping multichaind");

          try {
            // gracefully terminate by sending the RPC command to stop
            super.stop();
          } catch (MultiChainException e) {
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

    // not thread-safe
    private boolean checkedLifecyclePhaseTransition(LifecyclePhase to) {
      if (LIFECYCLE.get(this.multiChainLifecyclePhase).contains(to)) {
        this.multiChainLifecyclePhase = to;
        return true;
      }

      LOG.debug("Illegal lifecycle phase transition: {} -> {}", this.multiChainLifecyclePhase, to);
      return false;
    }

    // manages the transition from STOPPED -> STARTING -> RUNNING in a blocking fashion
    private void ensureRunning() {
      if (this.multiChainLifecyclePhase == LifecyclePhase.RUNNING
          || this.multiChainLifecyclePhase == LifecyclePhase.STOPPING) {
        // either we are running already, or we are not supposed to run anymore
        return;
      }

      synchronized (this.multiChainLifecyclePhaseTransition) {
        // check that we can transition to the STARTING phase (also fails if we are RUNNING
        // already) this check also fails if we are STARTING, which we might encounter since the
        // thread performing the start holds the lock and recurses into ensureRunning() during the
        // getBlockChainInfo() query (see below)
        if (this.checkedLifecyclePhaseTransition(LifecyclePhase.STARTING)) {
          // wait until the service is up
          this.multiChainDaemon.start();

          // now we set get the credentials from MultiChain
          this.setAuth();

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
            } catch (MultiChainException rpcException) {
              final MultiChainError rpcError = rpcException.getError();
              if (rpcError != null && rpcError.code() == MultiChainError.RPC_IN_WARMUP) {
                // keep waiting, multichaind is at work and will be with us soon
                LOG.debug(
                    "Waiting {} ms, multichaind is warming up ({})", backoff, rpcError.message());
                --failedRetries;
              } else if (!this.multiChainDaemon.isRunning()) {
                // multichaind has crashed
                this.forcedLifecyclePhaseTransition(LifecyclePhase.FAILED);
                throw new RuntimeException("multichaind stopped running");
              } else {
                // we do not know yet what is wrong, multichaind does not react to RPC calls
                LOG.debug("Waiting {} ms, multichaind has not started yet ({})", backoff,
                    rpcException.getMessage());
                LOG.debug(Markers.EXCEPTION, "multichaind has not started yet", rpcException);
              }

              // exponential backoff so we do not annoy multichaind too much
              try {
                // intentionally blocks all other threads
                Thread.sleep(backoff);
                backoff *= 2;
              } catch (InterruptedException e) {
                LOG.debug("Interrupted while waiting for multichaind to start: {}", e.getMessage());
                LOG.debug(
                    Markers.EXCEPTION, "Interrupted while waiting for multichaind to start", e);
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
    private void forcedLifecyclePhaseTransition(LifecyclePhase to) {
      if (!this.checkedLifecyclePhaseTransition(to)) {
        throw new IllegalStateException("Expected to be able to switch to " + to);
      }
    }

    private void setAuth() {
      // build the user:password information
      String userInfo;

      try {
        userInfo =
            this.multiChainDaemon.getMultiChainConf().getString(MultiChainOptions.RPC_USER_KEY);
      } catch (ConfigException.Missing e) {
        // no user given, do not use authentication
        return;
      }

      try {
        userInfo += ":"
            + this.multiChainDaemon.getMultiChainConf().getString(
                MultiChainOptions.RPC_PASSWORD_KEY);
      } catch (ConfigException.Missing e) {
        // no password given, proceed without it
      }

      super.setAuth(userInfo);
    }
  }

  private static class RemoteClient extends MultiChainJsonRpcClient {
    private RemoteClient(String protocol, Config config) throws MalformedURLException {
      super(new URL(protocol + "://" + config.getString(MultiChainOptions.RPC_USER_KEY) + ":"
          + config.getString(MultiChainOptions.RPC_PASSWORD_KEY) + "@"
          + config.getString(MultiChainOptions.RPC_CONNECT_KEY) + ":"
          + config.getInt(MultiChainOptions.RPC_PORT_KEY)));
    }
  }

  private static final Map<LifecyclePhase, Set<LifecyclePhase>> LIFECYCLE;

  private final Config config;

  private final MultiChainDaemon multiChainDaemon;

  private LifecyclePhase multiChainLifecyclePhase;

  private final Object multiChainLifecyclePhaseTransition;

  /**
   * Creates MultiChain clients.
   * @param config configuration containing the multichain-client options (see application.conf)
   */
  public MultiChainClientFactory(Config config) {
    this.config = config;

    String rpcConnect = "";
    try {
      rpcConnect = this.config.getString(MultiChainOptions.RPC_CONNECT_KEY);
    } catch (ConfigException.Missing e) {
      // handle not specified the same as empty
    }

    if ("".equals(rpcConnect)) {
      // start MultiChain locally if the target connect is empty
      this.multiChainDaemon = new MultiChainDaemon(this.config);
      this.multiChainLifecyclePhase = LifecyclePhase.STOPPED;
      this.multiChainLifecyclePhaseTransition = new Object();
    } else {
      // assume all is well if we connect to a remote MultiChain
      this.multiChainDaemon = null;
      this.multiChainLifecyclePhase = LifecyclePhase.RUNNING;
      this.multiChainLifecyclePhaseTransition = null;
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

  /**
   * Depending on the configuration, creates a local or remote client.
   * @return the constructed client
   */
  public MultiChainJsonRpcClient create() {
    if (this.multiChainDaemon != null) {
      try {
        return new LocalClient(this.getProtocol(), this.config, this.multiChainDaemon,
            this.multiChainLifecyclePhase, this.multiChainLifecyclePhaseTransition);
      } catch (MalformedURLException e) {
        throw new RuntimeException("Could not create local MultiChain client", e);
      }
    } else {
      try {
        return new RemoteClient(this.getProtocol(), this.config);
      } catch (MalformedURLException e) {
        throw new RuntimeException("Could not create remote MultiChain client", e);
      }
    }
  }

  private String getProtocol() {
    return this.config.hasPath(MultiChainOptions.RPC_SSL_KEY) ? "https" : "http";
  }
}
