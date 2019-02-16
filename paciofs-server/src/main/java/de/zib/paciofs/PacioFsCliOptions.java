/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

package de.zib.paciofs;

import java.io.File;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class PacioFsCliOptions {
  private static final String CONFIG = "config";
  private static final String CONFIG_SHORT = "c";
  private static final String HELP = "help";
  private static final String HELP_SHORT = "h";

  private final Options options;

  private final CommandLineParser commandLineParser;

  private ParseException parseException;

  private final HelpFormatter helpFormatter;

  private File config;

  private boolean help;

  /**
   * Encapsulates options that can be passed on the command line.
   */
  public PacioFsCliOptions() {
    this.options = new Options();
    this.options.addOption(CONFIG_SHORT, CONFIG, true, "path/to/paciofs.conf");
    this.options.addOption(HELP_SHORT, HELP, false, "print this message and exit");

    this.commandLineParser = new DefaultParser();
    this.helpFormatter = new HelpFormatter();
  }

  /**
   * Populates the options from the command line.
   * @param args the command line arguments to process
   * @return true if parsing succeeded, false otherwise
   */
  public boolean parseCommandLine(String[] args) {
    this.config = null;
    this.help = false;

    this.parseException = null;
    final CommandLine commandLine;
    try {
      commandLine = this.commandLineParser.parse(this.options, args, false);

      // do not expect any arguments
      final List<String> argList = commandLine.getArgList();
      if (argList.size() > 0) {
        throw new ParseException("Unrecognized argument(s): " + String.join(" ", argList));
      }

      // check for config file existence
      final String configPath = commandLine.getOptionValue(CONFIG);
      if (configPath != null) {
        this.config = new File(configPath);
        if (!this.config.exists()) {
          throw new ParseException("Config file does not exist: " + configPath);
        }
      }

      this.help = commandLine.hasOption(HELP);
      return true;
    } catch (ParseException e) {
      this.parseException = e;
      return false;
    }
  }

  public File getConfig() {
    return this.config;
  }

  public boolean getHelp() {
    return this.help;
  }

  /**
   * Prints the parsing error to stderr, if parsing did not succeed.
   */
  public void printError() {
    if (this.parseException != null) {
      System.err.println(this.parseException.getMessage());
    }
  }

  public void printHelp() {
    this.helpFormatter.printHelp("PacioFs", this.options);
  }
}
