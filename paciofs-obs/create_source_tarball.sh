#!/bin/bash

paciofs_version=$1
if [ -z "$1" ]; then
  echo "No version given, aborting"
  exit 1
fi

cd ..

# the directory we will put the necessary files in to build on OBS
DIST_DIR=$(mktemp -d -t paciofs.XXXXXX)
echo "Building distribution in ${DIST_DIR}"

# the OBS build VMs are offline, so copy necessary dependencies to distribution
mkdir ${DIST_DIR}/maven-repository
mvn --file ./paciofs-client/pom.xml \
  --activate-profiles docker \
  package org.apache.maven.plugins:maven-dependency-plugin:3.1.1:go-offline \
  --define maven.repo.local=${DIST_DIR}/maven-repository \
  --define skipTests=true

# create lean distribution
./paciofs-docker/make_dist.sh ${DIST_DIR}

# clone third parties into the distribution
mvn --file ${DIST_DIR}/paciofs-client/third_party/pom.xml initialize

DIST_ARCHIVE=$(pwd)/paciofs-obs/paciofs_${paciofs_version}.orig.tar.gz

cd ${DIST_DIR}
tar czf ${DIST_ARCHIVE} \
  pom.xml \
  paciofs-client \
  maven-repository

rm -rf ${DIST_DIR}
