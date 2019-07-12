/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "mount_options.h"

#include "options.h"

#include <boost/program_options.hpp>
#include <sstream>
#include <string>
#include <vector>

namespace paciofs {
namespace mount {
namespace options {

MountOptions::MountOptions()
    : paciofs::options::Options(),
      async_writes_(false),
      fuse_options_(std::vector<std::string>()),
      mount_point_(""),
      volume_name_("") {
  namespace bpo = boost::program_options;

  bpo::options_description mount_options("Mount Options");

  mount_options.add_options()(
      "async-writes", bpo::bool_switch(&async_writes_),
      "whether to return immediately after a write operation");

  // default fuse options
  fuse_options_.push_back("allow_other");
  fuse_options_.push_back("big_writes");
  fuse_options_.push_back("default_permissions");
  fuse_options_.push_back("fsname=paciofs");
  fuse_options_.push_back("max_readahead=1048576");
  fuse_options_.push_back("max_write=131072");
  fuse_options_.push_back("noatime");

  // build a textual representation to display the fuse options
  size_t size = fuse_options_.size();
  std::ostringstream default_fuse_options;
  if (size > 0) {
    default_fuse_options << fuse_options_[0];
    for (size_t i = 1; i < size; ++i) {
      default_fuse_options << "," << fuse_options_[i];
    }
  }

  mount_options.add_options()(
      "fuse-option,o",
      bpo::value<std::vector<std::string> >(&fuse_options_)
          ->default_value(fuse_options_, default_fuse_options.str())
          ->value_name("key|key=value"),
      "option to pass to fuse, may be specified repeatedly (once per "
      "key=value)");
  mount_options.add_options()(
      "mount-point",
      bpo::value<std::string>(&mount_point_)->value_name("path")->required(),
      "existing empty directory for mounting PacioFS");
  mount_options.add_options()(
      "volume-name",
      bpo::value<std::string>(&volume_name_)->value_name("name")->required(),
      "name of the volume to mount");

  options_.add(mount_options);

  positional_.add("mount-point", 1);
  positional_.add("volume-name", 1);
}

MountOptions::~MountOptions() {}

bool MountOptions::AsyncWrites() const { return async_writes_; }

std::vector<std::string> const& MountOptions::FuseOptions() const {
  return fuse_options_;
}

std::string const& MountOptions::MountPoint() const { return mount_point_; }

std::string const& MountOptions::VolumeName() const { return volume_name_; }

}  // namespace options
}  // namespace mount
}  // namespace paciofs
