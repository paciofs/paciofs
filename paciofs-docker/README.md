# paciofs-docker
Contains the necessary specifications to create and run a Docker image of PacioFS.

## [create_clean_source_distribution.sh](./create_clean_source_distribution.sh)
Uses Git to figure out files under source control and puts them into a new directory to allow a build from clean source.

## [docker-compose-minikube.sh](./docker-compose-minikube.sh)
Sets a `minikube` environment, builds the Docker image and puts it into the `minikube` registry.

## [docker-compose-push.sh](./docker-compose-push.sh)
Pushes the local Docker image to [Docker Hub](https://cloud.docker.com/u/paciofs/repository/docker/paciofs/paciofs).
If you want to build it there, push to the `docker-hub` branch.

## [docker-compose-up.sh](./docker-compose-up.sh)
Runs PacioFS locally on `minikube`.
