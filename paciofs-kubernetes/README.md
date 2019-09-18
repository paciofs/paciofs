# paciofs-kubernetes
Contains all files for deploying PacioFS to a [kubernetes](https://kubernetes.io) cluster.
Also check [paciofs-docker](../paciofs-docker/README.md).

## [paciofs.yaml.template](./paciofs.yaml.template)
See the [guide](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.15/) for details on the sections below.
Rendered via `mvn generate-resources` into a `minikube` version and a `kubernetes` version, which differ in the image pull policies.
Since the `minikube` image is put into the registry by [docker-compose-minikube.sh](../paciofs-docker/docker-compose-minikube.sh), there is no need to pull the image (it may even pull an outdated image), so the pull policy is set to `Never`.
When deploying to a remote k8s cluster, the pull policy is set to `Always`, which requires that the image has been pushed to the Docker Hub before using [docker-compose-push.sh](../paciofs-docker/docker-compose-push.sh).

When applied using `kubectl apply -f <file>`, the following steps are taken.
The `minikube` version uses a local `minikube` environment, the `kubernetes` version should be supplemented with a [configuration file](https://kubernetes.io/docs/concepts/configuration/organize-cluster-access-kubeconfig/) that specifies a target cluster.

- Create a namespace called `pacio` and label it `pacio`.
- Create a service that is reachable from the outside using HTTP and HTTPS.
- Deploys the Docker image using a replication factor of 1, wires all the ports together and opens the relevant ones for communication. Two probes check the health of the container (they are implemented by [Akka using the kubernetes cluster bootstrapping](https://doc.akka.io/docs/akka-management/current/bootstrap/kubernetes-api.html)).
- Create a role for reading Pods. This is necessary for Akka to be able to discover other containers in the deployment.
- Create a role binding to a default user that Akka can use for discovery.

A replication factor of more than 1 was not successful so far, because MultiChain nodes do not communicate properly, which is why we only use one node with one local MultiChain instance.
