/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "rpc_client.h"

#include "paciofs.grpc.pb.h"
#include "posix_io.grpc.pb.h"

#include <grpcpp/grpcpp.h>
#include <sys/types.h>
#include <unistd.h>
#include <fstream>
#include <sstream>
#include <string>

namespace paciofs {
namespace grpc {

template <typename Service>
RpcClient<Service>::RpcClient(std::string const &target,
                              std::string const &cert_chain,
                              std::string const &private_key,
                              std::string const &root_certs) {
  ::grpc::SslCredentialsOptions ssl;
  bool use_ssl = false;
  if (cert_chain.length() > 0) {
    ssl.pem_cert_chain = ReadPem(cert_chain);
    use_ssl = true;
  }
  if (private_key.length() > 0) {
    ssl.pem_private_key = ReadPem(private_key);
    use_ssl = true;
  }
  if (root_certs.length() > 0) {
    ssl.pem_root_certs = ReadPem(root_certs);
    use_ssl = true;
  }

  CreateMetadata();

  if (use_ssl) {
    stub_ = Service::NewStub(
        ::grpc::CreateChannel(target, ::grpc::SslCredentials(ssl)));
  } else {
    stub_ = Service::NewStub(
        ::grpc::CreateChannel(target, ::grpc::InsecureChannelCredentials()));
  }
}

template <typename Service>
std::string RpcClient<Service>::ReadPem(std::string const &path) {
  std::ifstream in;
  std::stringstream sstr;
  in.open(path);
  sstr << in.rdbuf();
  return sstr.str();
}

template <typename Service>
void RpcClient<Service>::CreateMetadata() {
  // TODO stop using the current user and group so all files can belong to us
  metadata_user_ = std::to_string(getuid());
  metadata_group_ = std::to_string(getgid());
}

template <typename Service>
std::unique_ptr<typename Service::Stub> const &RpcClient<Service>::Stub() {
  return stub_;
}

template <typename Service>
void RpcClient<Service>::SetMetadata(::grpc::ClientContext &context) {
  // We do not use call credentials because they get dropped automatically if
  // the channel is insecure for obvious reasons. So do not put any sensitive
  // information here.
  context.AddMetadata("x-user", metadata_user_);
  context.AddMetadata("x-group", metadata_group_);
}

template class RpcClient<PacioFsService>;
template class RpcClient<paciofs::io::posix::grpc::PosixIoService>;

}  // namespace grpc
}  // namespace paciofs
