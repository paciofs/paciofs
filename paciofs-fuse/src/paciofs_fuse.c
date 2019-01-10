/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "paciofs_fuse.h"

#include <sys/stat.h>

static struct fuse_operations paciofs_fuse_operations = {};

int main(int argc, char *argv[]) {
  umask(0);
  return fuse_main(argc, argv, &paciofs_fuse_operations, NULL);
}