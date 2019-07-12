/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "logging.h"
#include "mkfs_options.h"
#include "paciofs_rpc_client.h"

#include <exception>
#include <iostream>
#include <string>

int main(int argc, char *argv[]) {
  // parse command line without being strict, because we might just have to
  // display help or version
  paciofs::mkfs::options::MkfsOptions options;
  try {
    options.ParseCommandLine(argc, argv, false);
  } catch (std::invalid_argument &e) {
    // non-strictness only swallows missing required options only
    std::cerr << e.what() << std::endl;
    options.PrintHelp(std::string(argv[0]));
    return EXIT_FAILURE;
  }

  // only show help
  if (options.Help()) {
    options.PrintHelp(std::string(argv[0]));
    return EXIT_SUCCESS;
  }

  // only show version
  if (options.Version()) {
    std::cout << "PacioFS " << PACIOFS_VERSION << std::endl;
    return EXIT_SUCCESS;
  }

  // now that we did not exit early, we have to be strict
  try {
    options.ParseCommandLine(argc, argv, true);
  } catch (std::invalid_argument &e) {
    std::cerr << e.what() << std::endl;
    options.PrintHelp(std::string(argv[0]));
    return EXIT_FAILURE;
  }

  paciofs::logging::Initialize(options.LogLevel(), options.LogFile());
  paciofs::logging::Logger logger;

  // log invocation for later inspection
  logger.Debug([argc, argv](auto &out) {
    out << argv[0];
    for (int i = 1; i < argc; ++i) {
      out << " " << argv[i];
    }
  });

  // client to talk to PacioFS service
  std::string const &endpoint = options.Endpoint();
  paciofs::grpc::PacioFsRpcClient rpc_client(endpoint, options.PemCertChain(),
                                             options.PemPrivateKey(),
                                             options.PemRootCerts());

  // make sure we can talk to the service
  if (!rpc_client.Ping()) {
    logger.Fatal([endpoint](auto &out) {
      out << "Could not ping service at " << endpoint;
    });
    return EXIT_FAILURE;
  }

  // finally create the volume
  std::string const &name = options.Name();
  if (rpc_client.CreateVolume(name)) {
    logger.Debug(
        [name](auto &out) { out << "Successfully created volume " << name; });
  } else {
    logger.Fatal(
        [name](auto &out) { out << "Could not create volume " << name; });
    return EXIT_FAILURE;
  }

  return EXIT_SUCCESS;
}
