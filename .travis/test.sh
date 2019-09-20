#!/bin/bash

function stop_paciofs_server() {
  local paciofs_server_pid=$1
  kill "${paciofs_server_pid}"
  wait "${paciofs_server_pid}"
  local exit_code=$?

  # exit code for programs that are sent SIGTERM
  test "143" -eq "${exit_code}"
}

function create_file_system() {
  local file_system_name="$1"
  ./paciofs-client/target/Release/mkfs.paciofs localhost:8080 "${file_system_name}" -d TRACE
}

function mount_file_system() {
  local file_system_name="$1"
  local mount_point="$2"
  mkdir -p "${mount_point}"
  ./paciofs-client/target/Release/mount.paciofs localhost:8080 "${mount_point}" "${file_system_name}" -o default_permissions -o fsname=paciofs -o noatime -d TRACE &
  sleep 5s
}

function unmount_file_system() {
  local mount_point="$1"

  os=$(uname)
  if [[ "${os}" == "Linux" ]]; then
    fusermount -u "${mount_point}"
  elif [[ "${os}" == "Darwin" ]]; then
    diskutil unmount "${mount_point}"
  fi
}

# generate TLS certificates
cd ./paciofs-server/src/test/scripts && ./gen-certs.sh && cd ../../../../

# run tests
mvn test --batch-mode

# setup
mvn --projects paciofs-server exec:java@run-server &
paciofs_server_pid=$!
sleep 60s

create_file_system "volume1"
mount_file_system "volume1" "/tmp/mnt-volume1"

# create directory and empty file in it
mkdir /tmp/mnt-volume1/dir
touch /tmp/mnt-volume1/dir/file.empty
sync

# stop and delete data directory
unmount_file_system "/tmp/mnt-volume1"
stop_paciofs_server "${paciofs_server_pid}"
rm -rf /tmp/paciofs-data-dir

# restart server
mvn --projects paciofs-server exec:java@run-server &
paciofs_server_pid=$!
sleep 60s

# creation of volumes, directories and empty files should survive server restart even when losing the data directory
mount_file_system "volume1" "/tmp/mnt-volume1"
test -f /tmp/mnt-volume1/dir/file.empty

# write file
dd if=/dev/urandom of=/tmp/file.rnd bs=1048576 count=10
time cp /tmp/file.rnd /tmp/mnt-volume1/file.rnd
sync

# remount to clear caches
unmount_file_system "/tmp/mnt-volume1"
mount_file_system "volume1" "/tmp/mnt-volume1"

# files should not differ
diff /tmp/file.rnd /tmp/mnt-volume1/file.rnd; diff_exit=$?
test "0" -eq "${diff_exit}"

unmount_file_system "/tmp/mnt-volume1"
stop_paciofs_server "${paciofs_server_pid}"
