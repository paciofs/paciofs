#!/bin/bash

# figure out current directory
os=$(uname)
if [[ "${os}" == "Linux" ]]; then
  readlink_cmd=readlink
elif [[ "${os}" == "Darwin" ]]; then
  readlink_cmd=greadlink
fi
current_dir=$(dirname $(${readlink_cmd} -f $0))

docker-compose \
  --file "${current_dir}/docker-compose.yaml" \
  push paciofs
