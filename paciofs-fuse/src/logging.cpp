/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "logging.h"

#include <boost/date_time/posix_time/posix_time_types.hpp>
#include <boost/log/core/record.hpp>
#include <boost/log/expressions.hpp>
#include <boost/log/sources/record_ostream.hpp>
#include <boost/log/sources/severity_logger.hpp>
#include <boost/log/support/date_time.hpp>
#include <boost/log/utility/setup/common_attributes.hpp>
#include <boost/log/utility/setup/console.hpp>
#include <boost/log/utility/setup/file.hpp>
#include <exception>
#include <functional>
#include <iostream>
#include <string>

namespace paciofs {
namespace logging {

// convert integer log level to string for printing
std::ostream& operator<<(std::ostream& out, const Level& in) {
  if (in < 0 || in > 5) {
    throw std::out_of_range("in");
  }
  out << level_strings[in];
  return out;
}

// convert string log level to integer from input
std::istream& operator>>(std::istream& in, Level& out) {
  std::string token;
  in >> token;
  if ("TRACE" == token) {
    out = TRACE;
  } else if ("DEBUG" == token) {
    out = DEBUG;
  } else if ("INFO" == token) {
    out = INFO;
  } else if ("WARNING" == token) {
    out = WARNING;
  } else if ("ERROR" == token) {
    out = ERROR;
  } else if ("FATAL" == token) {
    out = FATAL;
  } else {
    throw std::invalid_argument(token);
  }
  return in;
}

void Initialize(Level level, std::string const& path) {
  namespace bl = boost::log;
  namespace ble = bl::expressions;

  // no path or stdout means cout
  // stderr means cerr
  bool out = path.length() == 0 || "stdout" == path;
  bool err = !out && "stderr" == path;
  if (out || err) {
    bl::add_console_log(
        out ? std::cout : std::cerr, bl::keywords::filter = severity >= level,
        bl::keywords::format =
            ble::stream << "[" << severity << "] ["
                        << ble::format_date_time<boost::posix_time::ptime>(
                               "TimeStamp", "%Y-%m-%d %H:%M:%S")
                        << "]: " << ble::smessage);
  } else {
    bl::add_file_log(
        bl::keywords::file_name = path, bl::keywords::auto_flush = true,
        bl::keywords::filter = severity >= level,
        bl::keywords::format =
            ble::stream << "[" << severity << "] ["
                        << ble::format_date_time<boost::posix_time::ptime>(
                               "TimeStamp", "%Y-%m-%d %H:%M:%S")
                        << "]: " << ble::smessage);
  }

  bl::add_common_attributes();
}

Logger::Logger() : logger_(boost::log::sources::severity_logger<Level>()) {}

Logger::~Logger() {}

void Logger::Trace(std::function<void(std::ostream&)> l) { Log(TRACE, l); }

void Logger::Debug(std::function<void(std::ostream&)> l) { Log(DEBUG, l); }

void Logger::Info(std::function<void(std::ostream&)> l) { Log(INFO, l); }

void Logger::Warning(std::function<void(std::ostream&)> l) { Log(WARNING, l); }

void Logger::Error(std::function<void(std::ostream&)> l) { Log(ERROR, l); }

void Logger::Fatal(std::function<void(std::ostream&)> l) { Log(FATAL, l); }

// calls the specified lambda with an output stream
void Logger::Log(Level level, std::function<void(std::ostream&)> l) {
  namespace bl = boost::log;

  bl::record record = logger_.open_record(bl::keywords::severity = level);
  if (record) {
    // if we get here, we passed the filter
    bl::record_ostream out(record);
    l(out.stream());
    out.flush();
    logger_.push_record(boost::move(record));
  }
}

}  // namespace logging
}  // namespace paciofs
