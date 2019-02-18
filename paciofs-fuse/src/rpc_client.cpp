/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "rpc_client.h"

#include "paciofs.grpc.pb.h"

#include <grpcpp/grpcpp.h>
#include <fstream>
#include <sstream>
#include <string>

namespace paciofs {
namespace grpc {

template <typename Service>
RpcClient<Service>::RpcClient(std::string const &target)
    : RpcClient(::grpc::CreateChannel(target,
                                      ::grpc::InsecureChannelCredentials())) {}

template <typename Service>
RpcClient<Service>::RpcClient(std::string const &target,
                              std::string const &cert_chain,
                              std::string const &private_key,
                              std::string const &root_certs) {
  ::grpc::SslCredentialsOptions ssl;
  if (cert_chain.length() > 0) {
    ssl.pem_cert_chain = ReadPem(cert_chain);
  }
  if (private_key.length() > 0) {
    ssl.pem_private_key = ReadPem(private_key);
  }
  if (root_certs.length() > 0) {
    ssl.pem_root_certs = ReadPem(root_certs);
  }

  stub_ = Service::NewStub(
      ::grpc::CreateChannel(target, ::grpc::SslCredentials(ssl)));
}

template <typename Service>
RpcClient<Service>::RpcClient(std::shared_ptr<::grpc::Channel> channel)
    : stub_(Service::NewStub(channel)) {}

template <typename Service>
std::string RpcClient<Service>::ReadPem(std::string const &path) {
  std::ifstream in;
  std::stringstream sstr;
  in.open(path);
  sstr << in.rdbuf();
  return sstr.str();
}

template class RpcClient<paciofs::io::posix::grpc::PosixIoService>;

}  // namespace grpc
}  // namespace paciofs
