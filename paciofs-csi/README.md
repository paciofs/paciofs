# paciofs-csi
This contains an integration with the [kubernetes](https://kubernetes.io) Container Storage Interface (see [here](https://kubernetes.io/blog/2019/01/15/container-storage-interface-ga/)).
Currently this is only a skeleton implementation.
Several components are needed to communicate with kubernetes, e.g. during volume creation.
Communication works with UNIX sockets, the `*server.go` components in the [source](./src/github.com/paciofs/paciofs/paciofs-csi/pfs) directory receive calls and should then interact with PacioFS.
However this interaction is not implemented at the moment.
