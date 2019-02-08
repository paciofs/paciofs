/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#ifndef POSIX_IO_RPC_CLIENT_H
#define POSIX_IO_RPC_CLIENT_H

#include "logging.h"
#include "posix_io.grpc.pb.h"

#include <grpcpp/grpcpp.h>
#include <sys/stat.h>
#include <string>

namespace paciofs {
namespace io {
namespace posix {
namespace grpc {

class PosixIoRpcClient {
 public:
  // uses insecure channel credentials
  explicit PosixIoRpcClient(std::string const& target);

  bool Ping();

  bool Stat(std::string path, struct stat* buf);

 private:
  explicit PosixIoRpcClient(std::shared_ptr<::grpc::Channel> channel);

  std::unique_ptr<PosixIoService::Stub> stub_;

  paciofs::logging::Logger logger_;
};

}  // namespace grpc
}  // namespace posix
}  // namespace io
}  // namespace paciofs

#endif  // POSIX_IO_RPC_CLIENT_H
