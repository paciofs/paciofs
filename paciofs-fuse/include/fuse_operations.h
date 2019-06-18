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


int PfsMkDir(const char *path, mode_t mode);

int PfsReadDir(const char *path, void *buf, fuse_fill_dir_t filler,
               off_t offset, struct fuse_file_info *fi);

#endif  // FUSE_OPERATIONS_H
