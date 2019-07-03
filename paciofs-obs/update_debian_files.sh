#!/bin/bash

paciofs_version=$1
if [ -z "$1" ]; then
	echo "No version given, aborting"
	exit 1
fi

OS=$(uname)
if [[ "$OS" == "Linux" ]]; then
  stat_format=("--format" "%s")
elif [[ "$OS" == "Darwin" ]]; then
  stat_format=("-f" "%z")
fi

paciofs_tarball=./paciofs_${paciofs_version}.orig.tar.gz

paciofs_tarball_md5sum=$(md5sum ${paciofs_tarball} | cut -d ' ' -f 1)
paciofs_tarball_size=$(stat ${stat_format[@]} ${paciofs_tarball})

paciofs_release_date=$(date -R)

PACIOFS_VERSION=${paciofs_version} PACIOFS_TARBALL_MD5SUM=${paciofs_tarball_md5sum} PACIOFS_TARBALL_SIZE=${paciofs_tarball_size} envsubst < ./paciofs.dsc.template > ./paciofs.dsc
PACIOFS_VERSION=${paciofs_version} PACIOFS_RELEASE_DATE=${paciofs_release_date} envsubst < ./debian.changelog.template > ./debian.changelog
