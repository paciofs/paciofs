#!/bin/bash

cat ./paciofs.yaml.template \
  | sed 's/\$IMAGE_PULL_POLICY'"/Never/g" \
  | sed 's/\$LOG_LEVEL'"/INFO/g" \
  | sed 's/\$PACIOFS_LOG_LEVEL'"/TRACE/g" \
> ./paciofs-minikube.yaml
