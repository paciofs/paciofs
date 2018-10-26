#!/bin/bash

DIR=$1
if [ -z "$DIR" ]; then
  echo "No directory given"
  exit 1
fi

# no need to format the dependencies
find "$DIR/src/github.com/paciofs/paciofs/paciofs-csi" -name '*.go' | grep --invert-match "vendor" | xargs gofmt -w
