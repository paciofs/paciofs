#!/bin/bash

paciofs_version=$1
if [[ -z "${paciofs_version}" ]]; then
	echo "No version given, aborting"
	exit 1
fi

os=$(uname)
if [[ "${os}" == "Linux" ]]; then
  stat_format=("--format" "%s")
  readlink_cmd=readlink
elif [[ "${os}" == "Darwin" ]]; then
  stat_format=("-f" "%z")
  readlink_cmd=greadlink
fi
current_dir=$(dirname $(${readlink_cmd} -f $0))

paciofs_tarball=${current_dir}/paciofs_${paciofs_version}.orig.tar.gz

paciofs_tarball_md5sum=$(md5sum ${paciofs_tarball} | cut -d ' ' -f 1)
paciofs_tarball_size=$(stat ${stat_format[@]} ${paciofs_tarball})

paciofs_release_date=$(date -R)

PACIOFS_VERSION=${paciofs_version} PACIOFS_TARBALL_MD5SUM=${paciofs_tarball_md5sum} PACIOFS_TARBALL_SIZE=${paciofs_tarball_size} envsubst < ${current_dir}/paciofs.dsc.template > ${current_dir}/paciofs.dsc
PACIOFS_VERSION=${paciofs_version} PACIOFS_RELEASE_DATE=${paciofs_release_date} envsubst < ${current_dir}/debian.changelog.template > ${current_dir}/debian.changelog
