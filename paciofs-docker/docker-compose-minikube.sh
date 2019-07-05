#!/bin/bash

usage() {
  echo "Usage: $0 [options]"
  echo "Options:"
  echo "  --log-level (default: not specified)"
  echo "  --no-clean (default: not specified)"
}

compose_options=""
build_options=""
while [[ $# -gt 0 ]]; do
  key="$1"
  case ${key} in
    --log-level)
      compose_options="${compose_options} --log-level $2"
      shift
      ;;
    --no-cache)
      build_options="${build_options} --no-cache"
      ;;
    *)
      echo "Invalid argument detected."
      usage
      exit 1
  esac
  shift
done

# figure out current directory
os=$(uname)
if [[ "${os}" == "Linux" ]]; then
  readlink_cmd=readlink
elif [[ "${os}" == "Darwin" ]]; then
  readlink_cmd=greadlink
fi
current_dir=$(dirname $(${readlink_cmd} -f $0))

if [[ -z "${DOCKER_HOST}" ]]; then
  echo "This does not look like a minikube environment, did you run 'eval \$(minikube docker-env)'?"
fi

# sets the environment variables used in the docker-compose command
echo "Setting minikube environment"
eval $(minikube docker-env)

# redirect to minikube Docker daemon
docker-compose --host "${DOCKER_HOST}" \
  --tls --tlscacert "${DOCKER_CERT_PATH}/ca.pem" --tlscert "${DOCKER_CERT_PATH}/cert.pem" --tlskey "${DOCKER_CERT_PATH}/key.pem" --tlsverify \
  --file "${current_dir}/docker-compose.yaml" \
  ${compose_options} \
  build ${build_options}
