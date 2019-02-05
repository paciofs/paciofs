/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#ifndef POSIX_IO_RPC_CLIENT_H
#define POSIX_IO_RPC_CLIENT_H

#include "posix_io.grpc.pb.h"

#include <grpcpp/grpcpp.h>
#include <sys/stat.h>
#include <string>

class PosixIoRpcClient {
 public:
  explicit PosixIoRpcClient(std::shared_ptr<grpc::Channel> channel);

  grpc::StatusCode Stat(std::string path, struct stat *buf);

 private:
  std::unique_ptr<io::posix::PosixIoService::Stub> stub_;
};

#endif  // POSIX_IO_RPC_CLIENT_H
