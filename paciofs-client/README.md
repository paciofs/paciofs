# paciofs-client
This project contains two utilities.
They communicate with the server utility using [gRPC](https://grpc.io).

## `mkfs.paciofs`
Used for creating a file system on a remote server, see [source](./src/mkfs_paciofs.cpp).
Sample invocation: `./target/Release/mkfs.paciofs localhost:8080 volume1`.

## `mount.paciofs`
Used for mounting a previously created file system, see [source](./src/mount_paciofs.cpp).
Sample invocation: `./target/Release/mount.paciofs localhost:8080 /tmp/mnt-volume1 volume1 -o default_permissions -o fsname=paciofs -o noatime`.
Fuse is used for passing any file system calls to the server, see [source](./src/fuse_operations.cpp).

## Building
Install third party dependencies first, then the actual client.
```bash
$ mvn --file ./third_party/pom.xml install
$ mvn install
```
