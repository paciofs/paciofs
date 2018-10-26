#!/bin/bash

DIR=$1
if [ -z "$DIR" ]; then
  echo "No directory given"
  exit 1
fi

find "$DIR/src/main/java" -name '*.java' | xargs clang-format -i
find "$DIR/src/main/proto" -name '*.proto' | xargs clang-format -i
