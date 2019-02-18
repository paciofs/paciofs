/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "paciofs_rpc_client.h"

#include "logging.h"
#include "paciofs.grpc.pb.h"
#include "rpc_client.h"

#include <grpcpp/grpcpp.h>
#include <string>

namespace paciofs {
namespace grpc {

PacioFsRpcClient::PacioFsRpcClient(std::string const &target)
    : RpcClient<PacioFsService>(target), logger_(paciofs::logging::Logger()) {}

PacioFsRpcClient::PacioFsRpcClient(std::string const &target,
                                   std::string const &cert_chain,
                                   std::string const &private_key,
                                   std::string const &root_certs)
    : RpcClient<PacioFsService>(target, cert_chain, private_key, root_certs),
      logger_(paciofs::logging::Logger()) {}

bool PacioFsRpcClient::CreateVolume(std::string const &name) {
  messages::Volume volume;
  volume.set_name(name);

  CreateVolumeRequest request;
  request.set_allocated_volume(&volume);
  logger_.Trace([request](auto &out) {
    out << "CreateVolume(" << request.ShortDebugString() << ")";
  });

  CreateVolumeResponse response;
  ::grpc::ClientContext context;
  ::grpc::Status status = stub_->CreateVolume(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "CreateVolume(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });
    return true;
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "CreateVolume(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });
    return false;
  }
}

bool PacioFsRpcClient::Ping() {
  PingRequest request;
  logger_.Trace([request](auto &out) {
    out << "Ping(" << request.ShortDebugString() << ")";
  });

  PingResponse response;
  ::grpc::ClientContext context;
  ::grpc::Status status = stub_->Ping(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "Ping(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "Ping(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });
  }

  return status.ok();
}

}  // namespace grpc
}  // namespace paciofs
