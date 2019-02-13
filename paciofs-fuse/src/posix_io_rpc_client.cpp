/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "posix_io_rpc_client.h"

#include "logging.h"
#include "posix_io.grpc.pb.h"

#include <grpcpp/grpcpp.h>
#include <sys/stat.h>
#include <exception>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

namespace paciofs {
namespace io {
namespace posix {
namespace grpc {

PosixIoRpcClient::PosixIoRpcClient(std::string const &target)
    : PosixIoRpcClient(::grpc::CreateChannel(
          target, ::grpc::InsecureChannelCredentials())) {}

PosixIoRpcClient::PosixIoRpcClient(std::string const &target,
                                   std::string const &cert_chain,
                                   std::string const &private_key,
                                   std::string const &root_certs)
    : logger_(paciofs::logging::Logger()) {
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

  stub_ = PosixIoService::NewStub(
      ::grpc::CreateChannel(target, ::grpc::SslCredentials(ssl)));
}

PosixIoRpcClient::PosixIoRpcClient(std::shared_ptr<::grpc::Channel> channel)
    : stub_(PosixIoService::NewStub(channel)),
      logger_(paciofs::logging::Logger()) {}

bool PosixIoRpcClient::Ping() {
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

messages::Errno PosixIoRpcClient::ReadDir(std::string const &path,
                                          std::vector<messages::Dir> &dirs) {
  ReadDirRequest request;
  request.set_path(path);
  logger_.Trace([request](auto &out) {
    out << "ReadDir(" << request.ShortDebugString() << ")";
  });

  ReadDirResponse response;
  ::grpc::ClientContext context;
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
  request.set_path(path);
  logger_.Trace([request](auto &out) {
    out << "Stat(" << request.ShortDebugString() << ")";
  });

  StatResponse response;
  ::grpc::ClientContext context;
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

std::string PosixIoRpcClient::ReadPem(std::string const &path) {
  std::ifstream in;
  std::stringstream sstr;
  in.open(path);
  sstr << in.rdbuf();
  return sstr.str();
}

}  // namespace grpc
}  // namespace posix
}  // namespace io
}  // namespace paciofs
