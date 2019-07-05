#!/bin/bash

paciofs_version=$1
if [[ -z "${paciofs_version}" ]]; then
  echo "No version given, aborting"
  exit 1
fi

os=$(uname)
if [[ "${os}" == "Linux" ]]; then
  readlink_cmd=readlink
elif [[ "${os}" == "Darwin" ]]; then
  readlink_cmd=greadlink
fi
current_dir=$(dirname $(${readlink_cmd} -f $0))

# the directory we will put the necessary files in to build on OBS
dist_dir=${current_dir}/dist
rm -rf ${dist_dir}
echo "Building distribution in ${dist_dir}"

# create lean distribution like in docker
${current_dir}/../paciofs-docker/make_dist.sh ${dist_dir}

# clone third parties into the distribution
mvn --file ${dist_dir}/paciofs-client/third_party/pom.xml initialize

# the OBS build VMs are offline, so copy necessary dependencies to distribution
mkdir ${dist_dir}/maven-repository
mvn --file ${dist_dir}/pom.xml \
  --projects paciofs-client,paciofs-server \
  --activate-profiles docker \
  org.apache.maven.plugins:maven-dependency-plugin:3.1.1:go-offline \
  --define maven.repo.local=${dist_dir}/maven-repository \
  --define skipTests=true

# remove all .git folders
git_files=$(find ${dist_dir} -name '.git')
for git_file in ${git_files}; do
  rm -rf ${git_file}
done

# package the whole thing
dist_archive=${current_dir}/paciofs_${paciofs_version}.orig.tar.gz

# change to parent directory of dist_dir so we can have relative paths inside the archive
tar czf ${dist_archive} -C $(dirname ${dist_dir}) $(basename ${dist_dir})

rm -rf ${dist_dir}
