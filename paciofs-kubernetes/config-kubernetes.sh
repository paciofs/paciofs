#!/bin/bash

cat ./paciofs.yaml.template \
  | sed 's/\$IMAGE_PULL_POLICY'"/Always/g" \
  | sed 's/\$LOG_LEVEL'"/INFO/g" \
  | sed 's/\$PACIOFS_LOG_LEVEL'"/INFO/g" \
> ./paciofs-kubernetes.yaml
