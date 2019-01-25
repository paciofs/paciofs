#!/bin/bash

cat ./paciofs.yaml.template \
  | sed 's/\$IMAGE_PULL_POLICY'"/Never/g" \
  | sed 's/\$PACIOFS_MULTICHAIN_LOG_LEVEL'"/DEBUG/g" \
> ./paciofs-minikube.yaml
