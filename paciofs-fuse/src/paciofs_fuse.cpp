/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "paciofs_fuse.h"
#include "io_posix.pb.h"

#include <sys/stat.h>

int paciofs_getattr(const char *__restrict__ path,
                    struct stat *__restrict__ buf) {
  return 0;
}

static struct fuse_operations paciofs_fuse_operations = {.getattr =
                                                             paciofs_getattr};

int main(int argc, char *argv[]) {
  umask(0);
  return fuse_main(argc, argv, &paciofs_fuse_operations, NULL);
}