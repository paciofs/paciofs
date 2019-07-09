# paciofs

[![Build Status](https://travis-ci.org/paciofs/paciofs.svg?branch=master)](https://travis-ci.org/paciofs/paciofs)

## Building and running locally on minikube
```bash
$ git clone https://github.com/paciofs/paciofs.git && cd ./paciofs
$ mvn --non-recursive install # install parent POM
$ mvn --file ./paciofs-client/third_party/pom.xml install # install client dependency libraries
$ export DESTDIR= # choose any prefix you like, default empty
$ mvn --define destdir=${DESTDIR} clean install # also installs client tools under ${DESTDIR}/usr/local/bin
$ minikube start
$ ./paciofs-docker/docker-compose-minikube.sh
$ kubectl apply -f ./paciofs-kubernetes/paciofs-minikube.yaml
```
## Creating and mounting the file system
```bash
$ kubectl port-forward --namespace=pacio service/paciofs 8080:8080
```
```bash
$ ${DESTDIR}/usr/local/bin/mkfs.paciofs localhost:8080 volume1
$ mkdir /tmp/volume1
$ ${DESTDIR}/usr/local/binmount.paciofs localhost:8080 /tmp/volume1 volume1 -d TRACE
```
