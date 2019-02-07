/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#ifndef OPTIONS_H
#define OPTIONS_H

#include "logging.h"

#include <boost/program_options.hpp>
#include <string>

namespace paciofs {
namespace options {

class Options {
 public:
  Options();

  virtual ~Options();

  // parses options and positional arguments; throws std::invalid_argument if
  // strict, tries to set at least help and version if not strict
  void ParseCommandLine(int argc, char* argv[], bool strict);

  // prints usage to stderr
  void PrintHelp(std::string const& executable) const;

  bool Help() const;

  std::string const& LogFile() const;

  logging::Level LogLevel() const;

  bool Version() const;

 protected:
  // holds options
  boost::program_options::options_description options_;

  // holds positional arguments
  boost::program_options::positional_options_description positional_;

 private:
  bool help_;
  std::string log_file_;
  logging::Level log_level_;
  bool version_;
};

}  // namespace options
}  // namespace paciofs

#endif  // OPTIONS_H
