/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#ifndef PACIOFS_RPC_CLIENT_H
#define PACIOFS_RPC_CLIENT_H

#include "logging.h"
#include "paciofs.grpc.pb.h"
#include "rpc_client.h"

#include <string>

namespace paciofs {
namespace grpc {

class PacioFsRpcClient : public RpcClient<PacioFsService> {
 public:
  explicit PacioFsRpcClient(std::string const& target,
                            std::string const& cert_chain,
                            std::string const& private_key,
                            std::string const& root_certs);

  bool Ping();

  bool CreateVolume(std::string const& name);

 private:
  paciofs::logging::Logger logger_;
};

}  // namespace grpc
}  // namespace paciofs

#endif  // PACIOFS_RPC_CLIENT_H
