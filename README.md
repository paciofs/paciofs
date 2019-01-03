# paciofs

## Building and running locally on minikube
```bash
$ git clone https://github.com/paciofs/paciofs.git && cd ./paciofs
$ mvn clean install
$ ./paciofs-docker/docker-compose-minikube.sh
$ kubectl apply -f ./paciofs-kubernetes/paciofs-minikube.yaml
$ kubectl port-forward --namespace=pacio service/paciofs 8080:8080
```
