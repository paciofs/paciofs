#!/bin/bash

GRPC_VERSION=$1
if [ -z "${GRPC_VERSION}" ]; then
  echo "No gRPC version specified"
  exit 1
fi

PROTOBUF_VERSION=$2
if [ -z "${PROTOBUF_VERSION}" ]; then
  echo "No Protobuf version specified"
  exit 1
fi

COMMAND=$3
if [ -z "${COMMAND}" ]; then
  echo "No command specified"
  exit 1
fi

# change to current directory so we can use relative paths
OS=$(uname)
if [[ "$OS" == "Linux" ]]; then
  READLINK=readlink
elif [[ "$OS" == "Darwin" ]]; then
  READLINK=greadlink
fi
cd $(dirname $(READLINK -f $0))

# install everything to third_party
third_party_dir=$(pwd)

if [ "${COMMAND}" = "initialize" ]; then
  git clone --branch v${GRPC_VERSION} https://github.com/grpc/grpc.git
  cd ./grpc && git submodule update --init
  cd ./third_party/protobuf && git checkout tags/v${PROTOBUF_VERSION}
fi

if [ "${COMMAND}" = "compile" ]; then
  cd ${third_party_dir}/grpc
  make prefix=${third_party_dir} HAS_SYSTEM_CARES=false HAS_SYSTEM_PROTOBUF=false PROTOBUF_CONFIG_OPTS="--prefix=${third_party_dir} --disable-maintainer-mode" -j2 --silent static plugins
fi

if [ "${COMMAND}" = "install" ]; then
  cd ${third_party_dir}/grpc
  make prefix=${third_party_dir} --silent install-headers_c install-static_c install-headers_cxx install-static_cxx install-plugins install-certs
  cd ./third_party/protobuf && make --silent install
fi
