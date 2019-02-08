/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "logging.h"
#include "mount_options.h"
#include "posix_io_rpc_client.h"

#include <fuse.h>
#include <sys/stat.h>
#include <exception>
#include <iostream>

static paciofs::io::posix::grpc::PosixIoRpcClient *g_client = nullptr;

static int paciofs_getattr(const char *__restrict__ path,
                           struct stat *__restrict__ buf) {
  g_client->Stat(std::string(path), buf);
  return 0;
}

int main(int argc, char *argv[]) {
  // parse command line without being strict, because we might just have to
  // display help or version
  paciofs::mount::options::MountOptions options;
  options.ParseCommandLine(argc, argv, false);

  // only show help
  if (options.Help()) {
    options.PrintHelp(std::string(argv[0]));
    return 0;
  }

  // declare here because we cannot pass NULL to fuse_main
  struct fuse_operations paciofs_fuse_operations;

  // only show version
  if (options.Version()) {
    std::cout << "PacioFS " << PACIOFS_VERSION << std::endl;

    // print fuse version as well
    return fuse_main(argc, argv, &paciofs_fuse_operations, nullptr);
  }

  // now that we did not exit early, we have to be strict
  try {
    options.ParseCommandLine(argc, argv, true);
  } catch (std::invalid_argument &e) {
    std::cerr << e.what() << std::endl;
    options.PrintHelp(std::string(argv[0]));
    return 1;
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

  // client to talk to I/O service
  std::string endpoint = options.Endpoint();
  g_client = new paciofs::io::posix::grpc::PosixIoRpcClient(endpoint);

  // make sure we can talk to the service
  if (!g_client->Ping()) {
    logger.Fatal([endpoint](auto &out) {
      out << "Could not ping service at " << endpoint;
    });
    return 1;
  }

  // no mask for newly created files
  umask(0);

  // set fuse operations
  paciofs_fuse_operations.getattr = paciofs_getattr;

  return fuse_main(argc, argv, &paciofs_fuse_operations, nullptr);
}
