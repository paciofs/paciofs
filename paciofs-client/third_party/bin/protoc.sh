#!/bin/bash

source_dir="$1"
shift
protoc_args="$@"

os=$(uname)
if [[ "${os}" == "Linux" ]]; then
  readlink_cmd=readlink
elif [[ "${os}" == "Darwin" ]]; then
  readlink_cmd=greadlink
fi
current_dir=$(dirname $(${readlink_cmd} -f $0))

if [[ ! -e "${current_dir}/protoc" ]]; then
  echo "protoc not found, install paciofs-client/third_party first"
  exit 1
fi

for file in $(find "${source_dir}" -name '*.proto'); do
  ${current_dir}/protoc ${protoc_args} "${file}"
done
