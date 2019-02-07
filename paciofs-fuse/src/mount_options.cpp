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
      endpoint_(""),
      fuse_options_(std::vector<std::string>()),
      mount_point_("") {
  namespace bpo = boost::program_options;

  bpo::options_description mount_options("Mount Options");

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
      "endpoint",
      bpo::value<std::string>(&endpoint_)->value_name("url")->required(),
      "URL pointing to a PacioFS service");
  mount_options.add_options()(
      "fuse-option,o",
      bpo::value<std::vector<std::string> >(&fuse_options_)
          ->default_value(fuse_options_, default_fuse_options.str())
          ->value_name("key=value"),
      "option to pass to fuse, can be specified multiple times");
  mount_options.add_options()(
      "mount-point",
      bpo::value<std::string>(&mount_point_)->value_name("path")->required(),
      "existing empty directory for mounting PacioFS");

  options_.add(mount_options);

  // positional arguments may be obtained from similarly named option as well
  positional_.add("endpoint", 1);
  positional_.add("mount-point", 1);
}

MountOptions::~MountOptions() {}

std::string const& MountOptions::Endpoint() const { return endpoint_; }

std::vector<std::string> const& MountOptions::FuseOptions() const {
  return fuse_options_;
}

std::string const& MountOptions::MountPoint() const { return mount_point_; }

}  // namespace options
}  // namespace mount
}  // namespace paciofs
