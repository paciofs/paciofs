#!/bin/bash

# figure out current directory
os=$(uname)
if [[ "${os}" == "Linux" ]]; then
  readlink_cmd=readlink
elif [[ "${os}" == "Darwin" ]]; then
  readlink_cmd=greadlink
fi
current_dir=$(dirname $(READLINK -f $0))

if [[ -z "${DOCKER_HOST}" ]]; then
  echo "This does not look like a minikube environment, did you run 'eval \$(minikube docker-env)'?"
fi

# sets the environment variables used in the docker-compose command
echo "Setting minikube environment"
eval $(minikube docker-env)

# redirect to minikube Docker daemon
# (the minikube VM has to have the necessary ports forwarded)
docker-compose --host "${DOCKER_HOST}" \
  --tls --tlscacert "${DOCKER_CERT_PATH}/ca.pem" --tlscert "${DOCKER_CERT_PATH}/cert.pem" --tlskey "${DOCKER_CERT_PATH}/key.pem" --tlsverify \
  --file "${current_dir}/docker-compose.yaml" \
  up paciofs
