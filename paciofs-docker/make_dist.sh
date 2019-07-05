#!/bin/bash

set -e

# directory to copy all files under source control into
dist_dir="$1"
if [[ -z "${dist_dir}" ]]; then
  echo "No target directory given"
  exit 1
fi

mkdir "${dist_dir}"

# directory of this script
# figure out current directory
os=$(uname)
if [[ "${os}" == "Linux" ]]; then
  readlink_cmd=readlink
elif [[ "${os}" == "Darwin" ]]; then
  readlink_cmd=greadlink
fi
current_dir=$(dirname $(${readlink_cmd} -f $0))

# make dist_dir absolute
dist_dir=$(${readlink_cmd} -f "${dist_dir}")

# get all files under version control
cd "${current_dir}/.."
files=$(git ls-files --cached --modified --others --exclude-standard)

# copy all files, making parent directories as we go
for file in ${files}; do
  directory=$(dirname "${dist_dir}/${file}")
  mkdir -p "${directory}"
  cp "${file}" "${dist_dir}/${file}"
done
