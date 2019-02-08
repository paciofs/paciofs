#!/bin/bash

PROTOC_BIN="$1"
shift
SOURCE_DIR="$1"
shift
ARGS="$@"

for file in $(find "${SOURCE_DIR}" -name '*.proto'); do
  "${PROTOC_BIN}" ${ARGS} "${file}"
done
