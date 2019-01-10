#!/bin/bash

# figure out current directory
OS=$(uname)
if [[ "$OS" == "Linux" ]]; then
  READLINK=readlink
elif [[ "$OS" == "Darwin" ]]; then
  READLINK=greadlink
fi
DIR=$(dirname $(READLINK -f $0))

docker-compose \
  --file "${DIR}/docker-compose.yaml" \
  push paciofs
