/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "paciofs_fuse.h"
#include "paciofs_client.h"

#include <grpcpp/grpcpp.h>
#include <sys/stat.h>

static PosixIoClient *g_client;

static int paciofs_getattr(const char *__restrict__ path,
                           struct stat *__restrict__ buf) {
  g_client->Stat(std::string(path), buf);
  return 0;
}

static struct fuse_operations paciofs_fuse_operations = {.getattr =
                                                             paciofs_getattr};

int main(int argc, char *argv[]) {
  umask(0);

  g_client = new PosixIoClient(grpc::CreateChannel(
      "localhost:8080", grpc::InsecureChannelCredentials()));

  return fuse_main(argc, argv, &paciofs_fuse_operations, NULL);
}