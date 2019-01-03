#!/bin/bash

# figure out current directory
OS=$(uname)
if [[ "$OS" == "Linux" ]]; then
  READLINK=readlink
elif [[ "$OS" == "Darwin" ]]; then
  READLINK=greadlink
fi
DIR=$(dirname $(READLINK -f $0))

if [ -z "${DOCKER_HOST}" ]; then
  echo "This does not look like a minikube environment, did you run 'eval \$(minikube docker-env)'?"
fi

# sets the environment variables used in the docker-compose command
echo "Setting minikube environment"
eval $(minikube docker-env)

# redirect to minikube Docker daemon
docker-compose --host "${DOCKER_HOST}" \
  --tls --tlscacert "${DOCKER_CERT_PATH}/ca.pem" --tlscert "${DOCKER_CERT_PATH}/cert.pem" --tlskey "${DOCKER_CERT_PATH}/key.pem" --tlsverify \
  --file "${DIR}/docker-compose.yaml" \
  build
