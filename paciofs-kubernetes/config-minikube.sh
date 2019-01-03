#!/bin/bash

cat ./paciofs.yaml.template | \
  sed 's/\$IMAGE_PULL_POLICY'"/Never/g" \
> ./paciofs-minikube.yaml
