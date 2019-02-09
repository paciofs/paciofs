/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "fuse_operations.h"
#include "logging.h"
#include "mount_options.h"
#include "posix_io_rpc_client.h"

#include <fuse.h>
#include <boost/filesystem.hpp>
#include <cerrno>
#include <cstdlib>
#include <exception>
#include <iostream>
#include <string>

int main(int argc, char *argv[]) {
  // parse command line without being strict, because we might just have to
  // display help or version
  paciofs::mount::options::MountOptions options;
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

  // declare here because we cannot pass nullptr to fuse_main
  struct fuse_operations operations = {};

  // only show version
  if (options.Version()) {
    std::cout << "PacioFS " << PACIOFS_VERSION << std::endl;

    // print fuse version as well
    return fuse_main(argc, argv, &operations, nullptr);
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

  // make sure mount point exists and is empty
  boost::filesystem::path mount_point(options.MountPoint());
  if (!boost::filesystem::exists(mount_point) ||
      !boost::filesystem::is_empty(mount_point)) {
    logger.Fatal([mount_point](auto &out) {
      out << "Mount point " << mount_point << " is not an empty directory";
    });
    return EXIT_FAILURE;
  }

  // construct arguments to pass to fuse
  struct fuse_args args = FUSE_ARGS_INIT(0, nullptr);

  // set binary name, it is ignored anyway
  fuse_opt_add_arg(&args, argv[0]);

  // convert all fuse options
  for (std::string const &arg : options.FuseOptions()) {
    fuse_opt_add_arg(&args, "-o");
    fuse_opt_add_arg(&args, arg.c_str());
  }

  // log arguments before checking them
  logger.Debug([args](auto &out) {
    out << "Invoking fuse as:";
    for (int i = 0; i < args.argc; ++i) {
      out << " " << args.argv[i];
    }
  });

  // sanity check arguments
  int error = fuse_opt_parse(&args, nullptr, nullptr, nullptr);
  if (error != 0) {
    logger.Fatal(
        [error](auto &out) { out << "fuse_opt_parse returned " << error; });
    fuse_opt_free_args(&args);
    return EXIT_FAILURE;
  }

  // client to talk to I/O service
  std::string const &endpoint = options.Endpoint();
  paciofs::io::posix::grpc::PosixIoRpcClient rpc_client(endpoint);

  // make sure we can talk to the service
  if (!rpc_client.Ping()) {
    logger.Fatal([endpoint](auto &out) {
      out << "Could not ping service at " << endpoint;
    });
    fuse_opt_free_args(&args);
    return EXIT_FAILURE;
  }

  // set up the operations
  InitializeFuseOperations(&rpc_client, operations);

  // no mask for newly created files
  umask(0);

  // create mount
  struct fuse_chan *chan = fuse_mount(mount_point.c_str(), &args);
  if (chan == nullptr) {
    error = errno;
    logger.Fatal([error](auto &out) {
      out << "fuse_mount returned " << std::strerror(error);
    });
    fuse_opt_free_args(&args);
    return EXIT_FAILURE;
  }

  // finally create the file system
  struct fuse *fs =
      fuse_new(chan, &args, &operations, sizeof(operations), nullptr);
  if (fs == nullptr) {
    error = errno;
    logger.Fatal([error](auto &out) {
      out << "fuse_new returned " << std::strerror(error);
    });
    fuse_opt_free_args(&args);
    fuse_unmount(mount_point.c_str(), chan);
    return EXIT_FAILURE;
  }

  fuse_opt_free_args(&args);

  // exit on HUP, TERM, INT and ignore PIPE
  error = fuse_set_signal_handlers(fuse_get_session(fs));
  if (error != 0) {
    logger.Warning([error](auto &out) {
      out << "fuse_set_signal_handlers returned " << error;
    });
  }

  // main loop
  error = fuse_loop_mt(fs);
  if (error != 0) {
    logger.Warning(
        [error](auto &out) { out << "fuse_loop_mt returned " << error; });
  }

  // what fuse_teardown does, except freeing the mount point
  fuse_remove_signal_handlers(fuse_get_session(fs));
  fuse_unmount(mount_point.c_str(), chan);
  fuse_destroy(fs);

  return EXIT_SUCCESS;
}
