# paciofs-kubernetes
Contains all files for deploying PacioFS to a [kubernetes](https://kubernetes.io) cluster.

## [paciofs.yaml.template](./paciofs.yaml.template)
See the [guide](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.15/) for details on the sections below.
Rendered via `mvn generate-resources` into a `minikube` version and a `kubernetes` version, which differ in the image pull policies.
When applied using `kubectl apply -f <file>`, the following steps are taken.
The `minikube` version uses a local `minikube` environment, the `kubernetes` version should be supplemented with a configuration file that specifies a target cluster.

- Create a namespace called `pacio` and label it `pacio`.
- Create a service that is reachable from the outside using HTTP and HTTPS.
- Deploys the Docker image using a replication factor of 1, wires all the ports together and opens the relevant ones for communication. Two probes check the health of the container.
- Create a role for reading Pods. This is necessary for Akka to be able to discover other containers in the deployment.
- Create a role binding to a default user that Akka can use for discovery.

A replication factor of more than 1 was not successful so far, because MultiChain nodes do not communicate properly, which is why we only use one node with one local MultiChain instance.