/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#ifndef PACIOFS_CLIENT_H
#define PACIOFS_CLIENT_H

#include "io_posix.grpc.pb.h"

#include <grpcpp/grpcpp.h>
#include <sys/stat.h>
#include <iostream>
#include <string>

class PosixIoClient {
 public:
  PosixIoClient(std::shared_ptr<grpc::Channel> channel)
      : stub_(io::posix::PosixIoService::NewStub(channel)) {}

  grpc::StatusCode Stat(std::string path, struct stat *buf) {
    io::posix::StatRequest request;
    request.set_path(path);

    io::posix::StatResponse response;

    grpc::ClientContext context;

    grpc::Status status = stub_->Stat(&context, request, &response);

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
    } else {
      std::cerr << "Stat: " << status.error_code() << ": "
                << status.error_message() << std::endl;
    }

    return status.error_code();
  }

 private:
  std::unique_ptr<io::posix::PosixIoService::Stub> stub_;
};

#endif  // PACIOFS_CLIENT_H