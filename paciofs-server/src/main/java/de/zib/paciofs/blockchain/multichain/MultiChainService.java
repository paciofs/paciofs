/*
 * Copyright (c) 2010, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs.blockchain.multichain;

import akka.event.LoggingAdapter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import de.zib.paciofs.blockchain.BlockchainService;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteStreamHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;

public class MultiChainService implements BlockchainService {
  private static final String MULTICHAIN_CONFIG_KEY = "paciofs.blockchain-service.multichain";

  private static final String MULTICHAIN_PATH_KEY = "path";

  private static final String MULTICHAIN_BLOCKCHAIN_NAME_KEY = "blockchain-name";
  private static final String MULTICHAIN_PROTOCOL_VERSION_KEY = "protocol-version";

  private static final String MULTICHAIND_OPTIONS_KEY = "multichaind.options";
  private static final String MULTICHAIN_UTIL_OPTIONS_KEY = "multichain-util.options";

  private Config config;

  private LoggingAdapter log;

  private ExecuteWatchdog watchdog;

  @Override
  public void configure(Config config, LoggingAdapter log) {
    this.config = config.getConfig(MULTICHAIN_CONFIG_KEY);
    this.log = log;

    this.log.debug("Configured MultiChain using: " + this.config.toString());
  }

  @Override
  public void start() {
    // see multichaind -?
    final CommandLine cmd =
        new CommandLine(new File(this.config.getString(MULTICHAIN_PATH_KEY), "multichaind"));

    // the only positional arguments are the name of the chain and the protocol
    // version
    cmd.addArgument(this.config.getString(MULTICHAIN_BLOCKCHAIN_NAME_KEY));
    cmd.addArgument(this.config.getString(MULTICHAIN_PROTOCOL_VERSION_KEY));

    // holds substitutions for the command line arguments we are building
    final Map<String, Object> substitutions = new HashMap<>();

    // build the remaining options
    final Config options = this.config.getConfig(MULTICHAIND_OPTIONS_KEY);
    for (Map.Entry<String, ConfigValue> entry : options.entrySet()) {
      final String key = entry.getKey();

      String value = entry.getValue().unwrapped().toString();

      switch (key) {
        case "daemon":
          // we are running multichaind in the background anyway
          this.log.warning("Ignoring -{} multichaind option", key);
          break;
        case "datadir":
          // add substitution and replace value with placeholder for
          // substitution
          substitutions.put(key, new File(value));
          value = "${" + key + "}";
          // fall-through
        default:
          cmd.addArgument("-" + key + (!"".equals(value) ? "=" + value : ""));
          break;
      }
    }
    cmd.setSubstitutionMap(substitutions);

    // make sure all directories and configuration files exist
    initializeBlockchain();

    final Executor executor = new DefaultExecutor();

    // redirect output to our log
    executor.setStreamHandler(new MultiChainExecuteStreamHandler(this.log));

    // watchdog used only for killing the process, so set the timeout to
    // infinity
    this.watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
    executor.setWatchdog(watchdog);

    final ExecuteResultHandler resultHandler = new DefaultExecuteResultHandler() {
      @Override
      public void onProcessComplete(int exitValue) {
        MultiChainService.this.log.info("multichaind completed with exit code: {}", exitValue);
      }

      @Override
      public void onProcessFailed(ExecuteException e) {
        MultiChainService.this.log.error(e, "multichaind failed");
        throw new RuntimeException("multichaind failed", e);
      }
    };

    this.log.info("Starting multichaind: {}", String.join(" ", cmd.toStrings()));
    try {
      // asynchronous execution to simulate -daemon behavior
      executor.execute(cmd, resultHandler);
    } catch (IOException e) {
      this.log.error(e, "multichaind failed");
      throw new RuntimeException("multichaind failed", e);
    }
  }

  @Override
  public void stop() {
    // TODO use API stop command
    // sends SIGTERM
    this.log.info("Stopping multichaind");
    this.watchdog.destroyProcess();
  }

  private void initializeBlockchain() {
    final Config options = this.config.getConfig(MULTICHAIN_UTIL_OPTIONS_KEY);

    // data directory needs to exist before creating the chain
    final File datadir = new File(options.getString("datadir"));
    if (!datadir.exists()) {
      this.log.info("Creating multichaind -datadir: {}", datadir.toString());
      if (!datadir.mkdirs()) {
        throw new RuntimeException("Could not create multichaind -datadir"
            + ": " + datadir.toString());
      }
    }

    final String chainName = this.config.getString(MULTICHAIN_BLOCKCHAIN_NAME_KEY);
    final File chaindir = new File(datadir, chainName);
    if (!chaindir.exists()) {
      // see multichain-util -?
      final CommandLine cmd =
          new CommandLine(new File(this.config.getString(MULTICHAIN_PATH_KEY), "multichain-util"));

      // all required positional arguments
      cmd.addArgument("create");
      cmd.addArgument(chainName);
      cmd.addArgument(this.config.getString(MULTICHAIN_PROTOCOL_VERSION_KEY));

      // build remaining options like above
      final Map<String, Object> substitutions = new HashMap<>();
      for (Map.Entry<String, ConfigValue> entry : options.entrySet()) {
        final String key = entry.getKey();
        String value = entry.getValue().unwrapped().toString();

        switch (key) {
          case "datadir":
            substitutions.put(key, datadir);
            value = "${" + key + "}";
            // fall-through
          default:
            cmd.addArgument("-" + key + "=" + value);
            break;
        }
      }
      cmd.setSubstitutionMap(substitutions);

      final Executor executor = new DefaultExecutor();

      this.log.info("Running multichain-util: {}", String.join(" ", cmd.toStrings()));
      try {
        // synchronous execution
        final int exitValue = executor.execute(cmd);
        this.log.info("multichain-util completed with exit code: {}", exitValue);
      } catch (IOException e) {
        this.log.error(e, "multichain-util failed");
        throw new RuntimeException("multichain-util failed", e);
      }
    }
  }

  private static class MultiChainExecuteStreamHandler implements ExecuteStreamHandler {
    private Thread out;
    private Thread err;

    private final LoggingAdapter log;

    public MultiChainExecuteStreamHandler(LoggingAdapter log) {
      this.log = log;
    }

    @Override
    public void setProcessInputStream(OutputStream outputStream) {
      // we are not sending any input to the process
    }

    @Override
    public void setProcessOutputStream(InputStream inputStream) {
      this.out = new Thread(
          new InputStreamToLogRedirector(inputStream, this.log::info), "multichaind.out");
    }

    @Override
    public void setProcessErrorStream(InputStream inputStream) {
      this.err = new Thread(
          new InputStreamToLogRedirector(inputStream, this.log::error), "multichaind.err");
    }

    @Override
    public void start() {
      this.out.start();
      this.err.start();
    }

    @Override
    public void stop() {
      this.out.interrupt();
      this.err.interrupt();

      while (true) {
        try {
          this.out.join();
          this.err.join();
          break;
        } catch (InterruptedException e) {
          this.log.debug(
              "Interrupted while waiting for logging threads to stop: {}", e.getMessage());
        }
      }
    }

    private static class InputStreamToLogRedirector implements Runnable {
      private final BufferedReader reader;
      private final Consumer<String> log;

      public InputStreamToLogRedirector(InputStream stream, Consumer<String> log) {
        this.reader = new BufferedReader(new InputStreamReader(stream));
        this.log = log;
      }

      @Override
      public void run() {
        while (true) {
          try {
            final String line = this.reader.readLine();
            if (line != null) {
              this.log.accept(line);
            } else {
              break;
            }
          } catch (IOException e) {
            break;
          }
        }
      }
    }
  }
}
