/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.blockchain.multichain;

import ch.qos.logback.classic.Level;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

  private static final Logger LOG = LoggerFactory.getLogger(MultiChaind.class);

  private final Config config;

  private ExecuteWatchdog watchdog;
  private DefaultExecuteResultHandler executeResultHandler;

  public MultiChaind(Config config) {
    this.config = config;
    LOG.debug("Configuration: {}", this.config.toString());
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

    // holds substitutions for the command line arguments we are building
    final Map<String, Object> substitutions = new HashMap<>();

    // build the remaining options
    final Config options = this.config.getConfig(MultiChainOptions.DAEMON_OPTIONS_KEY);
    for (Map.Entry<String, ConfigValue> entry : options.entrySet()) {
      final String key = entry.getKey();

      String value = entry.getValue().unwrapped().toString();

      switch (key) {
        case OPTION_DAEMON:
          // we are running multichaind in the background anyway
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

    // make sure all directories and configuration files exist
    this.initializeBlockchain();

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

    // apparently 141 is a pipe error which occurs during normal shutdown as well, so accept it
    executor.setExitValues(new int[] {0, 141});

    // invoked when the process is done, we use it to wait on the process before termination
    this.executeResultHandler = new DefaultExecuteResultHandler() {
      @Override
      public synchronized void onProcessComplete(int exitValue) {
        LOG.debug("multichaind completed with exit code: {}", exitValue);
        super.onProcessComplete(exitValue);
      }

      @Override
      public synchronized void onProcessFailed(ExecuteException e) {
        LOG.debug("multichaind failed with exit code: {} ({})", e.getExitValue(), e.getMessage());
        super.onProcessFailed(e);
      }
    };

    // watchdog used only for killing the process, so set the timeout to
    // infinity
    this.watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
    executor.setWatchdog(this.watchdog);

    LOG.debug("Starting multichaind: {}", String.join(" ", cmd.toStrings()));
    try {
      // asynchronous execution to simulate -daemon behavior
      executor.execute(cmd, this.executeResultHandler);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Terminates the MultiChain daemon via SIGTERM.
   */
  public void terminate() {
    LOG.debug("Stopping multichaind");

    // sends SIGTERM
    this.watchdog.destroyProcess();
    try {
      this.executeResultHandler.waitFor();
    } catch (InterruptedException e) {
      LOG.debug("Interrupted while waiting for multichaind to stop: {}", e.getMessage());
    }

    this.watchdog = null;
    this.executeResultHandler = null;
  }

  public boolean isRunning() {
    return this.watchdog != null && this.watchdog.isWatching();
  }

  private void initializeBlockchain() {
    final Config options = this.config.getConfig(MultiChainOptions.UTIL_OPTIONS_KEY);

    // data directory needs to exist before creating the chain
    final File datadir = new File(options.getString(OPTION_DATADIR));
    if (!datadir.exists()) {
      LOG.debug("Creating multichaind -datadir: {}", datadir.toString());
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

      // build remaining options like above
      final Map<String, Object> substitutions = new HashMap<>();
      for (Map.Entry<String, ConfigValue> entry : options.entrySet()) {
        final String key = entry.getKey();
        String value = entry.getValue().unwrapped().toString();

        switch (key) {
          case OPTION_DATADIR:
            substitutions.put(key, datadir);
            value = buildCommandLineSubstitution(key);
            // fall-through
          default:
            cmd.addArgument(buildCommandLineOption(key, value));
            break;
        }
      }
      cmd.setSubstitutionMap(substitutions);

      final Executor executor = new DefaultExecutor() {
        @Override
        protected Thread createThread(Runnable runnable, String name) {
          return super.createThread(runnable, "multichain-util.executor");
        }
      };

      LOG.debug("Running multichain-util: {}", String.join(" ", cmd.toStrings()));
      try {
        // synchronous execution
        final int exitValue = executor.execute(cmd);
        LOG.debug("multichain-util completed with exit code: {}", exitValue);
      } catch (IOException e) {
        throw new RuntimeException("multichain-util failed", e);
      }
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
