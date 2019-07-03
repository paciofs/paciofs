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
      endpoint_(""),
      help_(false),
      log_file_("stdout"),
      log_level_(logging::INFO),
      pem_cert_chain_(""),
      pem_private_key_(""),
      pem_root_certs_(""),
      tls_(false),
      version_(false) {
  namespace bpo = boost::program_options;

  bpo::options_description general_options("General Options");

  general_options.add_options()(
      "endpoint",
      bpo::value<std::string>(&endpoint_)->value_name("url")->required(),
      "URL pointing to a PacioFS service");
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
  general_options.add_options()(
      "log-level,d",
      bpo::value<logging::Level>(&log_level_)
          ->default_value(log_level_)
          ->value_name("level"),
      "TRACE, DEBUG, INFO, WARNING, ERROR, FATAL (also consider '--fuse-option "
      "debug' and the GRPC_VERBOSITY environment variable)");
  general_options.add_options()(
      "version,V", bpo::bool_switch(&version_)->default_value(version_),
      "print version and exit");

  bpo::options_description tls_options("TLS Options");
  tls_options.add_options()(
      "tls", bpo::bool_switch(&tls_)->default_value(tls_),
      "enable TLS (implicitly true if any of the following is set)");
  tls_options.add_options()("pem-cert-chain",
                            bpo::value<std::string>(&pem_cert_chain_)
                                ->default_value(pem_cert_chain_)
                                ->value_name("path"),
                            "path to client certificate chain PEM-file");
  tls_options.add_options()("pem-private-key",
                            bpo::value<std::string>(&pem_private_key_)
                                ->default_value(pem_private_key_)
                                ->value_name("path"),
                            "path to client private key PEM-file");
  tls_options.add_options()("pem-root-certs",
                            bpo::value<std::string>(&pem_root_certs_)
                                ->default_value(pem_root_certs_)
                                ->value_name("path"),
                            "path to trusted certificates PEM-file");

  options_.add(general_options);
  options_.add(tls_options);

  // positional arguments may be obtained from similarly named option as well
  positional_.add("endpoint", 1);
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

std::string const& Options::Endpoint() const { return endpoint_; }

bool Options::Help() const { return help_; }

std::string const& Options::LogFile() const { return log_file_; }

logging::Level Options::LogLevel() const { return log_level_; }

std::string const& Options::PemCertChain() const { return pem_cert_chain_; }

std::string const& Options::PemPrivateKey() const { return pem_private_key_; }

std::string const& Options::PemRootCerts() const { return pem_root_certs_; }

bool Options::Tls() const {
  return tls_ || pem_cert_chain_.length() > 0 ||
         pem_private_key_.length() > 0 || pem_root_certs_.length() > 0;
}

bool Options::Version() const { return version_; }

}  // namespace options
}  // namespace paciofs
