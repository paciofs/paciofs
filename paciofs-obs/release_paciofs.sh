#!/bin/bash

os=$(uname)
if [[ "${os}" == "Linux" ]]; then
  readlink_cmd=readlink
elif [[ "${os}" == "Darwin" ]]; then
  readlink_cmd=greadlink
fi
current_dir=$(dirname $(${readlink_cmd} -f $0))

echo "Enter new version number (e.g. 1.0.0):"
read paciofs_version

echo "Enter new release number (e.g. 1):"
read paciofs_release

echo "Creating source tarball ..."
${current_dir}/create_source_tarball.sh ${paciofs_version} ${paciofs_release}

echo "Updating debian files ..."
${current_dir}/update_debian_files.sh ${paciofs_version} ${paciofs_release}

echo "Deploying to OBS ..."
${current_dir}/deploy_paciofs.sh ${paciofs_version} ${paciofs_release}

echo "Done."
