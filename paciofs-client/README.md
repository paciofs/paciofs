# paciofs-client
This project contains two utilities.
They communicate with the server utility using [gRPC](https://grpc.io).

## `mkfs.paciofs`
Used for creating a file system on a remote server, see [mkfs_paciofs.cpp](./src/mkfs_paciofs.cpp).
Sample invocation: `./target/Release/mkfs.paciofs localhost:8080 volume1`.

## `mount.paciofs`
Used for mounting a previously created file system, see [mount_paciofs.cpp](./src/mount_paciofs.cpp).
Sample invocation: `./target/Release/mount.paciofs localhost:8080 /tmp/mnt-volume1 volume1 -o default_permissions -o fsname=paciofs -o noatime`.

Fuse is used for passing any file system calls to the server, see [fuse_operations.cpp](./src/fuse_operations.cpp), [paciofs_rpc_client.cpp](./src/paciofs_rpc_client.cpp) and [posix_io_rpc_client.cpp](./src/posix_io_rpc_client.cpp).

## Building
Install third party dependencies first, then the actual client.
```bash
$ mvn --file ./third_party/pom.xml install
$ mvn install
```
