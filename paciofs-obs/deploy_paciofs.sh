#!/bin/bash

paciofs_version=$1
if [[ -z "${paciofs_version}" ]]; then
  echo "No version given, aborting"
  exit 1
fi

paciofs_release=$2
if [[ -z "${paciofs_release}" ]]; then
  echo "No release given, aborting"
  exit 1
fi

os=$(uname)
if [[ "${os}" == "Linux" ]]; then
  readlink_cmd=readlink
elif [[ "${os}" == "Darwin" ]]; then
  readlink_cmd=greadlink
fi
current_dir=$(dirname $(${readlink_cmd} -f $0))

if [[ ! -e "${current_dir}/home:robert-schmidtke:paciofs" ]]; then
  echo "Please run osc co home:robert-schmidtke:paciofs first"
  exit 1
fi

cp ${current_dir}/paciofs.dsc ${current_dir}/home\:robert-schmidtke\:paciofs/paciofs
cp ${current_dir}/debian.changelog ${current_dir}/home\:robert-schmidtke\:paciofs/paciofs
cp ${current_dir}/debian.compat ${current_dir}/home\:robert-schmidtke\:paciofs/paciofs
cp ${current_dir}/debian.control ${current_dir}/home\:robert-schmidtke\:paciofs/paciofs
cp ${current_dir}/debian.rules ${current_dir}/home\:robert-schmidtke\:paciofs/paciofs
cp ${current_dir}/paciofs_${paciofs_version}-${paciofs_release}.orig.tar.gz ${current_dir}/home\:robert-schmidtke\:paciofs/paciofs

cd ${current_dir}/home\:robert-schmidtke\:paciofs/paciofs
osc add ./paciofs.dsc
osc add ./debian.changelog
osc add ./debian.compat
osc add ./debian.control
osc add ./debian.rules
osc add ./paciofs_${paciofs_version}-${paciofs_release}.orig.tar.gz

osc commit -m "PacioFS ${paciofs_version}-${paciofs_release}" \
  ./paciofs.dsc ./debian.changelog ./debian.compat ./debian.control ./debian.rules ./paciofs_${paciofs_version}-${paciofs_release}.orig.tar.gz
