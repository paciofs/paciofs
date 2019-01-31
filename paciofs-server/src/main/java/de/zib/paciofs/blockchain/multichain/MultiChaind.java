/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.blockchain.multichain;

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

public class MultiChaind {
  private static final String OPTION_DAEMON = "daemon";
  private static final String OPTION_DATADIR = "datadir";
  private static final String OPTION_RPCALLOW = "rpcallow";
  private static final String OPTION_RPCUSER = "rpcuser";
  private static final String OPTION_RPCPASSWORD = "rpcpassword";
  private static final String OPTION_SERVER = "server";

  private static final Logger LOG = LoggerFactory.getLogger(MultiChaind.class);

  private final Config config;

  private Config multiChainConf;

  private ExecuteWatchdog watchdog;
  private DefaultExecuteResultHandler executeResultHandler;

  public MultiChaind(Config config) {
    this.config = config;
    LOG.debug(Markers.CONFIGURATION, "Configuration: {}", this.config);
  }

  /**
   * Starts the MultiChain daemon in the background.
   */
  public void start() {
    // see multichaind -?
    final CommandLine cmd =
        new CommandLine(new File(this.config.getString(MultiChainOptions.PATH_KEY), "multichaind"));

    // the only positional arguments are the name of the chain and the protocol
    // version
    cmd.addArgument(this.config.getString(MultiChainOptions.BLOCKCHAIN_NAME_KEY));
    cmd.addArgument(this.config.getString(MultiChainOptions.PROTOCOL_VERSION_KEY));

    // multichaind-specific options
    this.addMultiChaindOptions(cmd);

    // make sure all directories and configuration files exist
    this.initializeBlockchain();

    // read multichain.conf that was generated for this chain
    this.readMultiChainConf();

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

    // invoked when the process is done, we use it to wait on the process before termination
    this.executeResultHandler = new DefaultExecuteResultHandler() {
      @Override
      public synchronized void onProcessFailed(ExecuteException e) {
        LOG.debug("multichaind failed with exit code: {} ({})", e.getExitValue(), e.getMessage());
        LOG.debug(Markers.EXCEPTION, "multichaind failed", e);
        super.onProcessFailed(e);
      }
    };

    // watchdog used only for monitoring the process, so set the timeout to
    // infinity
    this.watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
    executor.setWatchdog(this.watchdog);

    LOG.trace("Starting multichaind: {}", String.join(" ", cmd.toStrings()));
    try {
      // asynchronous execution to simulate -daemon behavior
      executor.execute(cmd, this.executeResultHandler);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    LOG.trace("Started multichaind");
  }

  /**
   * Waits for the MultiChain daemon to stop and obtains the exit value, if not interrupted.
   */
  public void waitForTermination() {
    try {
      this.executeResultHandler.waitFor();
      LOG.debug(
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

  public Config getMultiChainConf() {
    return this.multiChainConf;
  }

  private void initializeBlockchain() {
    final Config options = this.config.getConfig(MultiChainOptions.UTIL_OPTIONS_KEY);

    // data directory needs to exist before creating the chain
    final File datadir = new File(options.getString(OPTION_DATADIR));
    if (!datadir.exists()) {
      LOG.trace("Creating multichaind -datadir: {}", datadir.toString());
      if (!datadir.mkdirs()) {
        throw new RuntimeException(
            "Could not create multichaind -" + OPTION_DATADIR + ": " + datadir.toString());
      }
    }

    final String blockchainName = this.config.getString(MultiChainOptions.BLOCKCHAIN_NAME_KEY);
    final File chaindir = new File(datadir, blockchainName);
    if (!chaindir.exists()) {
      // see multichain-util -?
      final CommandLine cmd = new CommandLine(
          new File(this.config.getString(MultiChainOptions.PATH_KEY), "multichain-util"));

      // all required positional arguments
      cmd.addArgument("create");
      cmd.addArgument(blockchainName);
      cmd.addArgument(this.config.getString(MultiChainOptions.PROTOCOL_VERSION_KEY));

      // multichain-util-specific options
      this.addMultiChainUtilOptions(cmd);

      final Executor executor = new DefaultExecutor() {
        @Override
        protected Thread createThread(Runnable runnable, String name) {
          return super.createThread(runnable, "multichain-util.executor");
        }
      };

      LOG.trace("Running multichain-util: {}", String.join(" ", cmd.toStrings()));
      try {
        // synchronous execution
        final int exitValue = executor.execute(cmd);
        LOG.trace("multichain-util completed with exit code: {}", exitValue);
      } catch (IOException e) {
        throw new RuntimeException("multichain-util failed", e);
      }
    }
  }

  private void readMultiChainConf() {
    final Config options = this.config.getConfig(MultiChainOptions.UTIL_OPTIONS_KEY);
    final File blockchainDir = new File(options.getString(OPTION_DATADIR),
        this.config.getString(MultiChainOptions.BLOCKCHAIN_NAME_KEY));
    this.multiChainConf = ConfigFactory.parseFile(new File(blockchainDir, "multichain.conf"));
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
        case OPTION_DAEMON:
          // we are running multichaind in the background anyway, fall-through
        case OPTION_RPCALLOW:
          // we only allow localhost, fall-through
        case OPTION_RPCUSER:
          // we use multichaind generated rpcuser, fall-through
        case OPTION_RPCPASSWORD:
          // we use multichaind generated rpcpassword, fall-through
        case OPTION_SERVER:
          // we add the server option ourselves
          LOG.debug("Ignoring -{} multichaind option", key);
          break;
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
    cmd.addArgument(buildCommandLineOption(OPTION_RPCALLOW, "localhost"));
    try {
      final InetAddress localHost = InetAddress.getLocalHost();
      cmd.addArgument(buildCommandLineOption(OPTION_RPCALLOW, localHost.getHostName()));
      cmd.addArgument(buildCommandLineOption(OPTION_RPCALLOW, localHost.getHostAddress()));
    } catch (UnknownHostException e) {
      LOG.warn("Could not add all -{} options: {}", OPTION_RPCALLOW, e.getMessage());
      LOG.debug(Markers.EXCEPTION, "Could not get localhost", e);
    }
  }

  private void addMultiChainUtilOptions(CommandLine cmd) {
    final Config options = this.config.getConfig(MultiChainOptions.UTIL_OPTIONS_KEY);
    final File datadir = new File(options.getString(OPTION_DATADIR));

    // build remaining options like above
    final Map<String, Object> substitutions = new HashMap<>();
    for (Map.Entry<String, ConfigValue> entry : options.entrySet()) {
      final String key = entry.getKey();
      String value = entry.getValue().unwrapped().toString();

      // the only option we have to handle here
      if (OPTION_DATADIR.equals(key)) {
        substitutions.put(key, datadir);
        value = buildCommandLineSubstitution(key);
      }

      cmd.addArgument(buildCommandLineOption(key, value));
    }
    cmd.setSubstitutionMap(substitutions);
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
