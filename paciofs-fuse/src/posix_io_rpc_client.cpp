/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "posix_io_rpc_client.h"

#include "logging.h"

#include <grpcpp/grpcpp.h>
#include <sys/stat.h>
#include <string>

namespace paciofs {
namespace io {
namespace posix {
namespace grpc {

PosixIoRpcClient::PosixIoRpcClient(std::string const &target)
    : PosixIoRpcClient(::grpc::CreateChannel(
          target, ::grpc::InsecureChannelCredentials())) {}

PosixIoRpcClient::PosixIoRpcClient(std::shared_ptr<::grpc::Channel> channel)
    : stub_(PosixIoService::NewStub(channel)),
      logger_(paciofs::logging::Logger()) {}

bool PosixIoRpcClient::Stat(std::string path, struct stat *buf) {
  logger_.Trace([path](auto &out) { out << "stat(" << path << ")"; });

  StatRequest request;
  request.set_path(path);

  StatResponse response;

  ::grpc::ClientContext context;

  ::grpc::Status status = stub_->Stat(&context, request, &response);

  if (status.ok()) {
    buf->st_dev = response.stat().dev();
    buf->st_ino = response.stat().ino();
    buf->st_mode = response.stat().mode();
    buf->st_nlink = response.stat().nlink();
    buf->st_uid = response.stat().uid();
    buf->st_gid = response.stat().gid();
    buf->st_rdev = response.stat().rdev();
    buf->st_size = response.stat().size();

#if defined(__linux__)
    buf->st_atim.tv_sec = response.stat().atim().sec();
    buf->st_atim.tv_nsec = response.stat().atim().nsec();
    buf->st_mtim.tv_sec = response.stat().mtim().sec();
    buf->st_mtim.tv_nsec = response.stat().mtim().nsec();
    buf->st_ctim.tv_sec = response.stat().ctim().sec();
    buf->st_ctim.tv_nsec = response.stat().ctim().nsec();
#elif defined(__APPLE__)
    buf->st_atimespec.tv_sec = response.stat().atim().sec();
    buf->st_atimespec.tv_nsec = response.stat().atim().nsec();
    buf->st_mtimespec.tv_sec = response.stat().mtim().sec();
    buf->st_mtimespec.tv_nsec = response.stat().mtim().nsec();
    buf->st_ctimespec.tv_sec = response.stat().ctim().sec();
    buf->st_ctimespec.tv_nsec = response.stat().ctim().nsec();
#else
#error "Unsupported OS"
#endif

    buf->st_blksize = response.stat().blksize();
    buf->st_blocks = response.stat().blocks();
  }

  logger_.Trace([path, status](auto &out) {
    out << "stat(" << path << "): ";
    if (status.ok()) {
      out << "ok";
    } else {
      out << status.error_message() << " (" << status.error_code() << ")";
    }
  });

  return status.ok();
}

}  // namespace grpc
}  // namespace posix
}  // namespace io
}  // namespace paciofs
