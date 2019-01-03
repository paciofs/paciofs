#!/bin/bash

cat ./paciofs.yaml.template | \
  sed 's/\$IMAGE_PULL_POLICY'"/Always/g" \
> ./paciofs-kubernetes.yaml
