# PacioFS

[![Build Status](https://travis-ci.org/paciofs/paciofs.svg?branch=master)](https://travis-ci.org/paciofs/paciofs)

## Building and running locally on minikube
```bash
$ git clone https://github.com/paciofs/paciofs.git && cd ./paciofs

# install parent POM
$ mvn --non-recursive install

# install client dependency libraries
$ mvn --file ./paciofs-client/third_party/pom.xml install

# choose any prefix you like, default empty
$ export DESTDIR=

# also installs client tools under ${DESTDIR}/usr/local/bin
$ mvn --define destdir=${DESTDIR} clean install

$ minikube start
$ ./paciofs-docker/docker-compose-minikube.sh
$ kubectl apply -f ./paciofs-kubernetes/paciofs-minikube.yaml
```

## Creating and mounting the file system
```bash
$ kubectl port-forward --namespace=pacio service/paciofs 8080:8080

# in a new shell
$ ${DESTDIR}/usr/local/bin/mkfs.paciofs localhost:8080 volume1
$ mkdir /tmp/volume1
$ ${DESTDIR}/usr/local/bin/mount.paciofs localhost:8080 /tmp/volume1 volume1 -d TRACE
```

## Components
Also check [.travis.yml](./.travis.yml) as well as [test.sh](./.travis/test.sh) for building and testing.

### Client utilities
Platform specific client utilities for creating and mounting PacioFS: [paciofs-client](./paciofs-client/README.md).

### Kubernetes Container Storage Interface implementation
Skeleton implementation of k8s CSI: [paciofs-csi](./paciofs-csi/README.md).

### Docker image
Docker image and start/push scripts for docker-compose: [paciofs-docker](./paciofs-docker/README.md).

### Kubernetes configuration
Configuration files for minikube and remote k8s clusters: [paciofs-kubernetes](./paciofs-kubernetes/README.md).

### openSUSE Build Service configuration
Scripts and configuration files for building the client utilities on OBS: [paciofs-obs](./paciofs-obs/README.md).

### Server implementation
The actual file system server along with the MultiChain management: [paciofs-server](./paciofs-server/README.md).
