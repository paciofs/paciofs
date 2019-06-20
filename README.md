# paciofs

[![Build Status](https://travis-ci.org/paciofs/paciofs.svg?branch=master)](https://travis-ci.org/paciofs/paciofs)

## Building and running locally on minikube
```bash
$ git clone https://github.com/paciofs/paciofs.git && cd ./paciofs
$ mvn clean install
$ ./paciofs-docker/docker-compose-minikube.sh
$ kubectl apply -f ./paciofs-kubernetes/paciofs-minikube.yaml
```
## Creating and mounting the file system
```bash
$ kubectl port-forward --namespace=pacio service/paciofs 8080:8080
```
```bash
$ ./paciofs-fuse/target/Release/mkfs.paciofs localhost:8080 volume1
$ mkdir /tmp/volume1
$ ./paciofs-fuse/target/Release/mount.paciofs localhost:8080 /tmp/volume1 volume1 -d TRACE
```
