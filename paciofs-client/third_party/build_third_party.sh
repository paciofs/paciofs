#!/bin/bash

goal=$1
if [[ -z "${goal}" ]]; then
  echo "No goal specified"
  exit 1
fi

# for initialize
grpc_version=$2
protobuf_version=$3

# for compile
parallelism=$2

# change to current directory so we can use relative paths
os=$(uname)
if [[ "${os}" == "Linux" ]]; then
  readlink_cmd=readlink
elif [[ "${os}" == "Darwin" ]]; then
  readlink_cmd=greadlink
fi
cd $(dirname $(${readlink_cmd} -f $0))

# install everything to third_party
third_party_dir=$(pwd)

if [[ "${goal}" == "initialize" ]]; then
  if [[ -z "${grpc_version}" ]]; then
    echo "No gRPC version specified"
    exit 1
  fi

  if [[ -z "${protobuf_version}" ]]; then
    echo "No Protobuf version specified"
    exit 1
  fi

  git clone --branch v${grpc_version} https://github.com/grpc/grpc.git
  cd ./grpc && git submodule update --init
  cd ./third_party/protobuf && git checkout tags/v${protobuf_version}
fi

if [[ "${goal}" == "compile" ]]; then
  cd ${third_party_dir}/grpc
  make prefix=${third_party_dir} HAS_SYSTEM_CARES=false HAS_SYSTEM_PROTOBUF=false PROTOBUF_CONFIG_OPTS="--prefix=${third_party_dir} --disable-maintainer-mode" -j${parallelism} --silent static plugins
fi

if [[ "${goal}" == "install" ]]; then
  cd ${third_party_dir}/grpc
  make prefix=${third_party_dir} --silent install-headers_c install-static_c install-headers_cxx install-static_cxx install-plugins install-certs
  cd ./third_party/protobuf && make --silent install
fi
