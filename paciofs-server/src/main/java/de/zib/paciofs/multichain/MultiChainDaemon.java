/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.multichain;

import ch.qos.logback.classic.Level;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import de.zib.paciofs.logging.Markers;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiChainDaemon {
  // options passed to multichaind
  private static final String OPTION_DATADIR = "datadir";
  private static final String OPTION_RPCALLOWIP = "rpcallowip";
  private static final String OPTION_RPCPORT = "rpcport";
  private static final String OPTION_SERVER = "server";

  // keys used in multichain.conf
  private static final String RPC_USER_KEY = "rpcuser";
  private static final String RPC_PASSWORD_KEY = "rpcpassword";

  private static final Logger LOG = LoggerFactory.getLogger(MultiChainDaemon.class);

  private final Config config;

  private Config multiChainConf;

  private ExecuteWatchdog watchdog;
  private DefaultExecuteResultHandler executeResultHandler;

  public MultiChainDaemon(Config config) {
    this.config = config;
    LOG.debug(Markers.CONFIGURATION, "Configuration: {}", this.config);
  }

  /**
   * Starts the MultiChain daemon in the background.
   */
  public void start() {
    // see multichaind -?
    final CommandLine cmd =
        new CommandLine(new File(this.config.getString(MultiChainOptions.HOME_KEY), "multichaind"));

    final Config options = this.config.getConfig(MultiChainOptions.DAEMON_OPTIONS_KEY);

    // create data directory if necessary
    final File datadir = new File(options.getString(OPTION_DATADIR));
    if (!datadir.exists()) {
      LOG.info("Creating multichaind -datadir: {}", datadir.toString());
      if (!datadir.mkdirs()) {
        throw new RuntimeException(
            "Could not create multichaind -" + OPTION_DATADIR + ": " + datadir.toString());
      }

      // if we had to create the data directory, we need to specify a node to clone the chain from
      cmd.addArgument(this.config.getString(MultiChainOptions.CHAIN_NAME_KEY) + "@"
          + this.config.getString(MultiChainOptions.SEED_NODE_KEY));
    } else {
      // otherwise the name of the chain is sufficient
      cmd.addArgument(this.config.getString(MultiChainOptions.CHAIN_NAME_KEY));
    }

    // add the protocol version as second position argument
    cmd.addArgument(this.config.getString(MultiChainOptions.PROTOCOL_VERSION_KEY));

    // multichaind-specific options
    this.addMultiChaindOptions(cmd);

    // the executor we use to run multichaind in
    final Executor executor = new DefaultExecutor() {
      @Override
      protected Thread createThread(Runnable runnable, String name) {
        return super.createThread(runnable, "multichaind.executor");
      }
    };

    // redirect stdout and stderr to our LOG
    executor.setStreamHandler(new MultiChainPumpStreamHandler(
        new RedirectingOutputStream(LOG::debug, Level.INFO.levelInt),
        new RedirectingOutputStream(LOG::debug, Level.ERROR.levelInt)));

    // invoked when the process is done
    this.executeResultHandler = new DefaultExecuteResultHandler() {
      @Override
      public synchronized void onProcessFailed(ExecuteException e) {
        LOG.error("multichaind failed with exit code: {} ({})", e.getExitValue(), e.getMessage());
        LOG.error(Markers.EXCEPTION, "multichaind failed", e);
        super.onProcessFailed(e);
      }
    };

    // watchdog used only for monitoring the process, so set the timeout to
    // infinity
    this.watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
    executor.setWatchdog(this.watchdog);

    LOG.info("Starting multichaind: {}", String.join(" ", cmd.toStrings()));
    try {
      // asynchronous execution to simulate -daemon behavior
      executor.execute(cmd, this.executeResultHandler);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    LOG.info("Started multichaind");
  }

  /**
   * Waits for the MultiChain daemon to stop and obtains the exit value, if not interrupted.
   */
  public void waitForTermination() {
    try {
      this.executeResultHandler.waitFor();
      LOG.info(
          "multichaind completed with exit code: {}", this.executeResultHandler.getExitValue());
    } catch (InterruptedException e) {
      LOG.debug("Interrupted while waiting for multichaind to stop: {}", e.getMessage());
      LOG.debug(Markers.EXCEPTION, "Interrupted while waiting for multichaind to stop", e);
    }
    this.executeResultHandler = null;

    this.watchdog.stop();
    this.watchdog = null;
  }

  public boolean isRunning() {
    return this.watchdog != null && this.watchdog.isWatching();
  }

  public String getRpcUser() {
    return this.getMultiChainConf().getString(RPC_USER_KEY);
  }

  public String getRpcPassword() {
    return this.getMultiChainConf().getString(RPC_PASSWORD_KEY);
  }

  public int getRpcPort() {
    return this.config.getConfig(MultiChainOptions.DAEMON_OPTIONS_KEY).getInt(OPTION_RPCPORT);
  }

  private Config getMultiChainConf() {
    if (this.multiChainConf == null || this.multiChainConf.isEmpty()) {
      synchronized (this) {
        if (this.multiChainConf == null || this.multiChainConf.isEmpty()) {
          final Config options = this.config.getConfig(MultiChainOptions.DAEMON_OPTIONS_KEY);
          final File datadir = new File(options.getString(OPTION_DATADIR),
              this.config.getString(MultiChainOptions.CHAIN_NAME_KEY));
          this.multiChainConf = ConfigFactory.parseFile(new File(datadir, "multichain.conf"));
        }
      }
    }

    return this.multiChainConf;
  }

  private void addMultiChaindOptions(CommandLine cmd) {
    // holds substitutions for the command line arguments we are building
    final Map<String, Object> substitutions = new HashMap<>();

    // build the remaining options
    final Config options = this.config.getConfig(MultiChainOptions.DAEMON_OPTIONS_KEY);
    for (Map.Entry<String, ConfigValue> entry : options.entrySet()) {
      final String key = entry.getKey();

      String value = entry.getValue().unwrapped().toString();

      switch (key) {
        case OPTION_DATADIR:
          // add substitution and replace value with placeholder for
          // substitution
          substitutions.put(key, new File(value));
          value = buildCommandLineSubstitution(key);
          // fall-through
        default:
          cmd.addArgument(buildCommandLineOption(key, value));
          break;
      }
    }
    cmd.setSubstitutionMap(substitutions);

    // serve JSON RPCs
    cmd.addArgument(buildCommandLineOption(OPTION_SERVER, ""));

    // only allow RPC commands from our host
    try {
      final InetAddress localHost = InetAddress.getLocalHost();
      cmd.addArgument(buildCommandLineOption(OPTION_RPCALLOWIP, localHost.getHostAddress()));
    } catch (UnknownHostException e) {
      LOG.warn("Could not add -{} option: {}", OPTION_RPCALLOWIP, e.getMessage());
      LOG.warn(Markers.EXCEPTION, "Could not get localhost", e);
    }
  }

  private static String buildCommandLineOption(String option, String argument) {
    return "-" + option + (!"".equals(argument) ? "=" + argument : "");
  }

  private static String buildCommandLineSubstitution(String argument) {
    return "${" + argument + "}";
  }

  private static class RedirectingOutputStream extends LogOutputStream {
    private final Consumer<String> log;

    private RedirectingOutputStream(final Consumer<String> log, final int level) {
      super(level);
      this.log = log;
    }

    @Override
    protected void processLine(final String line, final int level) {
      this.log.accept(line);
    }
  }

  private static class MultiChainPumpStreamHandler extends PumpStreamHandler {
    private String nextThreadName;

    private MultiChainPumpStreamHandler(OutputStream out, OutputStream err) {
      super(out, err);
      this.nextThreadName = null;
    }

    @Override
    protected void createProcessOutputPump(InputStream is, OutputStream os) {
      this.nextThreadName = "multichaind.out";
      super.createProcessOutputPump(is, os);
      this.nextThreadName = null;
    }

    @Override
    protected void createProcessErrorPump(InputStream is, OutputStream os) {
      this.nextThreadName = "multichaind.err";
      super.createProcessErrorPump(is, os);
      this.nextThreadName = null;
    }

    @Override
    protected Thread createPump(InputStream is, OutputStream os, boolean closeWhenExhausted) {
      final Thread result = super.createPump(is, os, closeWhenExhausted);
      result.setName(this.nextThreadName);
      return result;
    }
  }
}
