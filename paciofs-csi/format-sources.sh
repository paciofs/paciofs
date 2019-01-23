#!/bin/bash

# no need to format the dependencies
find "$(pwd)/src/github.com/paciofs/paciofs/paciofs-csi" -name '*.go' | grep --invert-match "vendor" | xargs gofmt -w
