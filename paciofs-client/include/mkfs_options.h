/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#ifndef MKFS_OPTIONS_H
#define MKFS_OPTIONS_H

#include "options.h"

#include <string>

namespace paciofs {
namespace mkfs {
namespace options {

class MkfsOptions : public paciofs::options::Options {
 public:
  MkfsOptions();

  ~MkfsOptions();

  std::string const& Name() const;

 private:
  std::string name_;
};

}  // namespace options
}  // namespace mkfs
}  // namespace paciofs

#endif  // MKFS_OPTIONS_H
