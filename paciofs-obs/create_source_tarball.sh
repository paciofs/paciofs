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

cmake_version=$3
if [[ -z "${cmake_version}" ]]; then
  echo "No cmake version given, aborting"
  exit 1
fi

mvn_version=$4
if [[ -z "${mvn_version}" ]]; then
  echo "No mvn version given, aborting"
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
dist_dir=${current_dir}/paciofs-${paciofs_version}
if [[ -e "${dist_dir}" ]]; then
  echo "${dist_dir} exists"
  exit 1
fi
echo "Building distribution in ${dist_dir}"

# create lean distribution like in docker
${current_dir}/../paciofs-docker/create_clean_source_distribution.sh ${dist_dir}

# clone third parties into the distribution
mvn --file ${dist_dir}/paciofs-client/third_party/pom.xml initialize

# the OBS build VMs are offline, so copy necessary dependencies to distribution
mkdir ${dist_dir}/maven-repository
mvn --file ${dist_dir}/pom.xml \
  --projects paciofs-client \
  --activate-profiles docker \
  org.apache.maven.plugins:maven-dependency-plugin:3.1.1:go-offline \
  --define maven.repo.local=${dist_dir}/maven-repository \
  --define skipTests=true

# remove all .git folders
git_files=$(find ${dist_dir} -name '.git')
for git_file in ${git_files}; do
  rm -rf ${git_file}
done

# ship cmake
wget https://github.com/Kitware/CMake/releases/download/v${cmake_version}/cmake-${cmake_version}-Linux-x86_64.tar.gz
tar xf cmake-${cmake_version}-Linux-x86_64.tar.gz -C ${dist_dir}
rm cmake-${cmake_version}-Linux-x86_64.tar.gz

# ship maven
wget https://www-eu.apache.org/dist/maven/maven-3/${mvn_version}/binaries/apache-maven-${mvn_version}-bin.zip
unzip apache-maven-${mvn_version}-bin.zip -d ${dist_dir}
rm apache-maven-${mvn_version}-bin.zip

# package the whole thing
dist_archive=${current_dir}/paciofs_${paciofs_version}-${paciofs_release}.orig.tar.gz

# change to parent directory of dist_dir so we can have relative paths inside the archive
echo "Creating ${dist_archive} ..."
tar czf ${dist_archive} -C $(dirname ${dist_dir}) $(basename ${dist_dir})
