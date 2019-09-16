#!/bin/bash

os=$(uname)
if [[ "${os}" == "Linux" ]]; then
  umount_cmd=fusermount
  umount_options=-u
elif [[ "${os}" == "Darwin" ]]; then
  umount_cmd=diskutil
  umount_options=unmount
fi

# generate TLS certificates
cd ./paciofs-server/src/test/scripts && ./gen-certs.sh && cd ../../../../

# run tests
mvn test --batch-mode

# start and stop PacioFS
mvn --projects paciofs-server exec:java@run-server &
mvn_pid=$!
sleep 60s

# create and mount file system
./paciofs-client/target/Release/mkfs.paciofs localhost:8080 volume1 -d TRACE
mkdir -p /tmp/mnt-volume1
./paciofs-client/target/Release/mount.paciofs localhost:8080 /tmp/mnt-volume1 volume1 -o default_permissions -o fsname=paciofs -o noatime -d TRACE &
sleep 5s

# write file
dd if=/dev/urandom of=/tmp/file.rnd bs=1048576 count=10
# dd if=/dev/urandom of=/tmp/file.rnd bs=1024 count=32
time cp /tmp/file.rnd /tmp/mnt-volume1/file.rnd
sync

# remount to clear caches
${umount_cmd} ${umount_options} /tmp/mnt-volume1
./paciofs-client/target/Release/mount.paciofs localhost:8080 /tmp/mnt-volume1 volume1 -o default_permissions -o fsname=paciofs -o noatime -d TRACE &
sleep 5s

# files should not differ
diff /tmp/file.rnd /tmp/mnt-volume1/file.rnd; diff_exit=$?
test "0" -eq "${diff_exit}"

# unmount
${umount_cmd} ${umount_options} /tmp/mnt-volume1

# wait for everyting to shut down
kill ${mvn_pid}; wait ${mvn_pid}; mvn_exit=$?

# JVM return code when sent SIGTERM
test "143" -eq "${mvn_exit}"
