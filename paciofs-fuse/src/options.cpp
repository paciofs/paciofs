/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "options.h"

#include "logging.h"

#include <boost/program_options.hpp>
#include <exception>
#include <iostream>
#include <string>

namespace paciofs {
namespace options {

Options::Options()
    : options_(boost::program_options::options_description()),
      positional_(boost::program_options::positional_options_description()),
      help_(false),
      log_file_("stdout"),
      log_level_(logging::INFO),
      version_(false) {
  namespace bpo = boost::program_options;

  bpo::options_description general_options("General Options");

  general_options.add_options()("help,h",
                                bpo::bool_switch(&help_)->default_value(help_),
                                "print this message and exit");
  general_options.add_options()(
      "log-file,l",
      bpo::value<std::string>(&log_file_)
          ->default_value(log_file_)
          ->value_name("path"),
      "path to log file; special values 'stdout' and 'stderr' log to standard "
      "output and error, respectively; defaults to 'stdout'");
  general_options.add_options()("log-level,d",
                                bpo::value<logging::Level>(&log_level_)
                                    ->default_value(log_level_)
                                    ->value_name("level"),
                                "TRACE, DEBUG, INFO, WARNING, ERROR, FATAL");
  general_options.add_options()(
      "version,V", bpo::bool_switch(&version_)->default_value(version_),
      "print version and exit");

  options_.add(general_options);
}

Options::~Options() {}

void Options::ParseCommandLine(int argc, char* argv[], bool strict) {
  namespace bpo = boost::program_options;

  bpo::variables_map vm;
  bpo::store(bpo::command_line_parser(argc, argv)
                 .options(options_)
                 .positional(positional_)
                 .run(),
             vm);
  try {
    // notify checks required options before calling notify on each value
    bpo::notify(vm);
  } catch (bpo::required_option& e) {
    if (strict) {
      // convert boost exceptions into standard exceptions
      throw std::invalid_argument(e.what());
    } else {
      // set help and version manually in case this is why required options are
      // missing
      help_ = vm.count("help") > 0 && vm["help"].as<bool>();
      version_ = vm.count("version") > 0 && vm["version"].as<bool>();
    }
  }
}

void Options::PrintHelp(std::string const& executable) const {
  // assemble usage string ourselves
  std::cerr << "Usage: " << executable << " [options]";
  for (size_t i = 0; i < positional_.max_total_count(); ++i) {
    std::cerr << " <" << positional_.name_for_position(i) << ">";
  }

  // options are nicely printed by boost
  std::cerr << std::endl << options_;
}

bool Options::Help() const { return help_; }

std::string const& Options::LogFile() const { return log_file_; }

logging::Level Options::LogLevel() const { return log_level_; }

bool Options::Version() const { return version_; }

}  // namespace options
}  // namespace paciofs
