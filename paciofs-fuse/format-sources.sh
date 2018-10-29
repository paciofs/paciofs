#!/bin/bash

DIR=$1
if [ -z "$DIR" ]; then
  echo "No directory given"
  exit 1
fi

find "$DIR/include/" -name '*.h' | xargs clang-format -i
find "$DIR/src/" -name '*.c' | xargs clang-format -i

