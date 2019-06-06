/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "posix_io_rpc_client.h"

#include "logging.h"
#include "posix_io.grpc.pb.h"
#include "rpc_client.h"

#include <grpcpp/grpcpp.h>
#include <sys/stat.h>
#include <string>
#include <vector>

namespace paciofs {
namespace io {
namespace posix {
namespace grpc {

PosixIoRpcClient::PosixIoRpcClient(std::string const &target,
                                   std::string const &volume_name)
    : paciofs::grpc::RpcClient<PosixIoService>(target),
      volume_name_(volume_name),
      logger_(paciofs::logging::Logger()) {}

PosixIoRpcClient::PosixIoRpcClient(std::string const &target,
                                   std::string const &volume_name,
                                   std::string const &cert_chain,
                                   std::string const &private_key,
                                   std::string const &root_certs)
    : paciofs::grpc::RpcClient<PosixIoService>(target, cert_chain, private_key,
                                               root_certs),
      volume_name_(volume_name),
      logger_(paciofs::logging::Logger()) {}

bool PosixIoRpcClient::Ping() {
  PingRequest request;
  logger_.Trace([request](auto &out) {
    out << "Ping(" << request.ShortDebugString() << ")";
  });

  PingResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
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

messages::Errno PosixIoRpcClient::ReadDir(std::string const &path,
                                          std::vector<messages::Dir> &dirs) {
  ReadDirRequest request;
  request.set_path(PreparePath(path));
  logger_.Trace([request](auto &out) {
    out << "ReadDir(" << request.ShortDebugString() << ")";
  });

  ReadDirResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = stub_->ReadDir(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "ReadDir(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });

    messages::Errno error = response.error();
    if (error != messages::ERRNO_ESUCCESS) {
      return error;
    }

    for (int i = 0; i < response.dirs_size(); ++i) {
      dirs.push_back(response.dirs(i));
    }

    return messages::ERRNO_ESUCCESS;
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "ReadDir(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

messages::Errno PosixIoRpcClient::Stat(std::string const &path,
                                       messages::Stat &stat) {
  StatRequest request;
  request.set_path(PreparePath(path));
  logger_.Trace([request](auto &out) {
    out << "Stat(" << request.ShortDebugString() << ")";
  });

  StatResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = stub_->Stat(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "Stat(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });

    messages::Errno error = response.error();
    if (error != messages::ERRNO_ESUCCESS) {
      return error;
    }

    stat.CopyFrom(response.stat());
    return messages::ERRNO_ESUCCESS;
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "Stat(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

messages::Errno PosixIoRpcClient::MkDir(std::string const &path,
                                        google::protobuf::uint32 mode) {
  MkDirRequest request;
  request.set_path(PreparePath(path));
  request.set_mode(mode);
  logger_.Trace([request](auto &out) {
    out << "MkDir(" << request.ShortDebugString() << ")";
  });

  MkDirResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = stub_->MkDir(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "MkDir(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });

    return response.error();
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "MkDir(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

std::string const PosixIoRpcClient::PreparePath(std::string const &path) const {
  std::string prepared_path = path;
  prepared_path = MakeAbsolute(prepared_path);
  prepared_path = PrefixVolumeName(prepared_path);
  return prepared_path;
}

std::string const PosixIoRpcClient::MakeAbsolute(std::string const &path) {
  return path;
}

std::string const PosixIoRpcClient::PrefixVolumeName(
    std::string const &path) const {
  return volume_name_ + ":" + path;
}

}  // namespace grpc
}  // namespace posix
}  // namespace io
}  // namespace paciofs
