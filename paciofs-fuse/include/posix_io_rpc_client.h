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
#include "rpc_client.h"

#include <string>
#include <vector>

namespace paciofs {
namespace io {
namespace posix {
namespace grpc {

class PosixIoRpcClient : public paciofs::grpc::RpcClient<PosixIoService> {
 public:
  // uses insecure channel credentials
  explicit PosixIoRpcClient(std::string const& target,
                            std::string const& volume_name);

  // uses TLS
  explicit PosixIoRpcClient(std::string const& target,
                            std::string const& volume_name,
                            std::string const& cert_chain,
                            std::string const& private_key,
                            std::string const& root_certs);

  bool Ping();

  messages::Errno ReadDir(std::string const& path,
                          std::vector<messages::Dir>& dirs);

  messages::Errno Stat(std::string const& path, messages::Stat& stat);

 private:
  std::string volume_name_;
  paciofs::logging::Logger logger_;
};

}  // namespace grpc
}  // namespace posix
}  // namespace io
}  // namespace paciofs

#endif  // POSIX_IO_RPC_CLIENT_H
