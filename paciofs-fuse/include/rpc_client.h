/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#ifndef RPC_CLIENT
#define RPC_CLIENT

#include <grpcpp/grpcpp.h>
#include <string>

namespace paciofs {
namespace grpc {

template <typename Service>
class RpcClient {
 public:
  // uses insecure channel credentials
  explicit RpcClient(std::string const& target);

  // uses TLS
  explicit RpcClient(std::string const& target, std::string const& cert_chain,
                     std::string const& private_key,
                     std::string const& root_certs);

 private:
  explicit RpcClient(std::shared_ptr<::grpc::Channel> channel);

  std::string ReadPem(std::string const& path);

  void CreateMetadata();

  // sent in each request as metadata
  std::string metadata_user_;
  std::string metadata_group_;

 protected:
  std::unique_ptr<typename Service::Stub> stub_;

  void SetMetadata(::grpc::ClientContext& context);
};

}  // namespace grpc
}  // namespace paciofs

#endif  // RPC_CLIENT
