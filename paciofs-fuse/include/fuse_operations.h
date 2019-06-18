/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#ifndef FUSE_OPERATIONS_H
#define FUSE_OPERATIONS_H

#include "posix_io_rpc_client.h"

#include <fuse.h>
#include <sys/stat.h>

struct fuse_operations_context {
  paciofs::io::posix::grpc::PosixIoRpcClient *rpc_client;
};

void InitializeFuseOperations(
    paciofs::io::posix::grpc::PosixIoRpcClient *rpc_client,
    fuse_operations &operations);

int PfsGetAttr(const char *path, struct stat *buf);

int PfsMkNod(const char *path, mode_t mode, dev_t dev);

int PfsMkDir(const char *path, mode_t mode);

int PfsChMod(const char *path, mode_t mode);

int PfsChOwn(const char *path, uid_t uid, gid_t gid);

int PfsReadDir(const char *path, void *buf, fuse_fill_dir_t filler,
               off_t offset, struct fuse_file_info *fi);

#endif  // FUSE_OPERATIONS_H
