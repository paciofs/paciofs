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
  explicit PosixIoRpcClient(std::string const& target,
                            std::string const& volume_name, bool async_writes,
                            std::string const& cert_chain,
                            std::string const& private_key,
                            std::string const& root_certs);

  ~PosixIoRpcClient();

  bool Ping();

  messages::Errno Stat(std::string const& path, messages::Stat& stat);

  messages::Errno MkNod(std::string const& path, google::protobuf::uint32 mode,
                        google::protobuf::int32 dev);

  messages::Errno MkDir(std::string const& path, google::protobuf::uint32 mode);

  messages::Errno ChMod(std::string const& path, google::protobuf::uint32 mode);

  messages::Errno ChOwn(std::string const& path, google::protobuf::uint32 uid,
                        google::protobuf::uint32 gid);

  messages::Errno Open(std::string const& path, google::protobuf::int32 flags,
                       google::protobuf::uint64& fh);

  messages::Errno Read(std::string const& path, char* buf,
                       google::protobuf::uint32 size,
                       google::protobuf::int64 offset,
                       google::protobuf::uint64 fh,
                       google::protobuf::uint32& n);

  messages::Errno Write(std::string const& path, const char* buf,
                        google::protobuf::uint32 size,
                        google::protobuf::int64 offset,
                        google::protobuf::uint64 fh,
                        google::protobuf::uint32& n);

  messages::Errno ReadDir(std::string const& path,
                          std::vector<messages::Dir>& dirs);

  messages::Errno Create(std::string const& path, google::protobuf::uint32 mode,
                         google::protobuf::int32 flags,
                         google::protobuf::uint64& fh);

 private:
  std::string const PreparePath(std::string const& path) const;

  static std::string const MakeAbsolute(std::string const& path);

  std::string const PrefixVolumeName(std::string const& path) const;

  bool async_writes_;
  std::unique_ptr<::grpc::CompletionQueue> async_write_completion_queue_;
  std::string volume_name_;
  paciofs::logging::Logger logger_;
};

}  // namespace grpc
}  // namespace posix
}  // namespace io
}  // namespace paciofs

#endif  // POSIX_IO_RPC_CLIENT_H
