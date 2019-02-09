/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#ifndef LOGGING_H
#define LOGGING_H

#include <boost/log/expressions/keyword.hpp>
#include <boost/log/sources/severity_logger.hpp>
#include <functional>
#include <iostream>
#include <string>

namespace paciofs {
namespace logging {

enum Level { TRACE, DEBUG, INFO, WARNING, ERROR, FATAL };

// for converting our levels to nice log messages
const std::string LEVEL_STRINGS[] = {"TRACE",   "DEBUG", "INFO",
                                     "WARNING", "ERROR", "FATAL"};
std::ostream& operator<<(std::ostream& out, const Level& in);

// for converting log level strings into actual levels
std::istream& operator>>(std::istream& in, Level& out);

// adds a sink to a file, or cout/cerr if path is stdout/stderr
void Initialize(Level level, std::string const& path);

// makes the severity keyword available in log filters as our level
BOOST_LOG_ATTRIBUTE_KEYWORD(severity, "Severity", Level)

class Logger {
 public:
  Logger();

  virtual ~Logger();

  void Trace(std::function<void(std::ostream&)> l);
  void Debug(std::function<void(std::ostream&)> l);
  void Info(std::function<void(std::ostream&)> l);
  void Warning(std::function<void(std::ostream&)> l);
  void Error(std::function<void(std::ostream&)> l);
  void Fatal(std::function<void(std::ostream&)> l);

 private:
  void Log(Level level, std::function<void(std::ostream&)> l);

  boost::log::sources::severity_logger<Level> logger_;
};

}  // namespace logging
}  // namespace paciofs

#endif  // LOGGING_H
