#!/bin/bash

OS=$(uname)
if [[ "$OS" == "Linux" ]]; then
  UMOUNT_CMD=fusermount
  UMOUNT_OPTIONS=-u
elif [[ "$OS" == "Darwin" ]]; then
  UMOUNT_CMD=diskutil
  UMOUNT_OPTIONS=unmount
fi

# generate TLS certificates
cd ./paciofs-server/src/test/scripts && ./gen-certs.sh && cd ../../../../

# run tests
mvn test --batch-mode

# start and stop PacioFS
mvn --projects paciofs-server exec:java@run-server &
MVN_PID=$!
sleep 60s

# create and mount file system
./paciofs-client/target/Release/mkfs.paciofs localhost:8080 volume1 -d TRACE
mkdir /tmp/mnt-volume1
./paciofs-client/target/Release/mount.paciofs localhost:8080 /tmp/mnt-volume1 volume1 -o default_permissions -o fsname=paciofs -o noatime -d TRACE &
sleep 5s

# write file
cp ./pom.xml /tmp/mnt-volume1
sync

# remount to clear caches
${UMOUNT_CMD} ${UMOUNT_OPTIONS} /tmp/mnt-volume1
./paciofs-client/target/Release/mount.paciofs localhost:8080 /tmp/mnt-volume1 volume1 -o default_permissions -o fsname=paciofs -o noatime -d TRACE &
sleep 5s

# files should not differ
diff ./pom.xml /tmp/mnt-volume1/pom.xml; DIFF_EXIT=$?
test "0" -eq "${DIFF_EXIT}"

# unmount
${UMOUNT_CMD} ${UMOUNT_OPTIONS} /tmp/mnt-volume1

# wait for everyting to shut down
kill ${MVN_PID}; wait ${MVN_PID}; MVN_EXIT=$?

# JVM return code when sent SIGTERM
test "143" -eq "${MVN_EXIT}"
