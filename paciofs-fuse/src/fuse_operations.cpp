/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "fuse_operations.h"

#include "posix_io.grpc.pb.h"
#include "posix_io_rpc_client.h"

#include <cerrno>
#include <string>
#include <vector>

static struct fuse_operations_context g_context { .rpc_client = nullptr };

static int ToErrno(paciofs::io::posix::grpc::messages::Errno error) {
#ifdef VERBATIM_ERRNO
  return error;
#else
  namespace messages = paciofs::io::posix::grpc::messages;

  switch (error) {
    case messages::ERRNO_ESUCCESS:
      return 0;
    case messages::ERRNO_ENOENT:
      return ENOENT;
    case messages::ERRNO_EIO:
      return EIO;
    default:
      throw std::invalid_argument(messages::Errno_Name(error));
  }
#endif  // VERBATIM_ERRNO
}

void InitializeFuseOperations(
    paciofs::io::posix::grpc::PosixIoRpcClient *rpc_client) {
  g_context.rpc_client = rpc_client;
}

int PfsGetAttr(const char *path, struct stat *buf) {
  namespace messages = paciofs::io::posix::grpc::messages;

  messages::Stat s;
  messages::Errno error = g_context.rpc_client->Stat(std::string(path), s);
  if (error != messages::ERRNO_ESUCCESS) {
    return -ToErrno(error);
  }

  // TODO add VERBATIM_MODE etc.
  buf->st_dev = s.dev();
  buf->st_ino = s.ino();
  buf->st_mode = s.mode();
  buf->st_nlink = s.nlink();
  buf->st_uid = s.uid();
  buf->st_gid = s.gid();
  buf->st_rdev = s.rdev();
  buf->st_size = s.size();
#if defined(__linux__)
  buf->st_atim.tv_sec = s.atim().sec();
  buf->st_atim.tv_nsec = s.atim().nsec();
  buf->st_mtim.tv_sec = s.mtim().sec();
  buf->st_mtim.tv_nsec = s.mtim().nsec();
  buf->st_ctim.tv_sec = s.ctim().sec();
  buf->st_ctim.tv_nsec = s.ctim().nsec();
#elif defined(__APPLE__)
  buf->st_atimespec.tv_sec = s.atim().sec();
  buf->st_atimespec.tv_nsec = s.atim().nsec();
  buf->st_mtimespec.tv_sec = s.mtim().sec();
  buf->st_mtimespec.tv_nsec = s.mtim().nsec();
  buf->st_ctimespec.tv_sec = s.ctim().sec();
  buf->st_ctimespec.tv_nsec = s.ctim().nsec();
#else
#error "Unsupported OS"
#endif
  buf->st_blksize = s.blksize();
  buf->st_blocks = s.blocks();

  return 0;
}

int PfsReadDir(const char *path, void *buf, fuse_fill_dir_t filler,
               off_t offset, struct fuse_file_info *fi) {
  namespace messages = paciofs::io::posix::grpc::messages;

  std::vector<messages::Dir> dirs;
  messages::Errno error =
      g_context.rpc_client->ReadDir(std::string(path), dirs);
  if (error != messages::ERRNO_ESUCCESS) {
    return -ToErrno(error);
  }

  for (messages::Dir const &dir : dirs) {
    filler(buf, dir.name().c_str(), nullptr, 0);
  }

  return 0;
}
