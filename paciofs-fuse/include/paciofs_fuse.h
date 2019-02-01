/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#ifndef PACIOFS_FUSE_H
#define PACIOFS_FUSE_H

#define FUSE_USE_VERSION 26
#include <fuse.h>
#include <sys/stat.h>

int paciofs_getattr(const char *__restrict__ path,
                    struct stat *__restrict__ buf);

#endif  // PACIOFS_FUSE_H