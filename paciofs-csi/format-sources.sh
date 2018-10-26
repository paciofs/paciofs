#!/bin/bash

DIR=$1
if [ -z "$DIR" ]; then
  echo "No directory given"
  exit 1
fi

find "$DIR/src/github.com/paciofs/paciofs/paciofs-csi" -name '*.go' | xargs gofmt -w
