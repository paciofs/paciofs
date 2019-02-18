/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "mkfs_options.h"

#include "options.h"

#include <boost/program_options.hpp>
#include <string>

namespace paciofs {
namespace mkfs {
namespace options {

MkfsOptions::MkfsOptions() : paciofs::options::Options(), name_("") {
  namespace bpo = boost::program_options;

  bpo::options_description mkfs_options("Mkfs Options");

  mkfs_options.add_options()("name,n",
                             bpo::value<std::string>(&name_)->required(),
                             "name of the volume to create");

  options_.add(mkfs_options);

  positional_.add("name", 1);
}

MkfsOptions::~MkfsOptions() {}

std::string const& MkfsOptions::Name() const { return name_; }

}  // namespace options
}  // namespace mkfs
}  // namespace paciofs
