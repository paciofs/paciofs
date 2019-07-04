#!/bin/bash

paciofs_version=$1
if [ -z "$1" ]; then
  echo "No version given, aborting"
  exit 1
fi

cd ..

# the directory we will put the necessary files in to build on OBS
DIST_DIR=$(mktemp -d -t paciofs.XXXXXX)

# the OBS build VMs are offline, so copy necessary dependencies to distribution
mvn --file ./paciofs-client/pom.xml \
  package org.apache.maven.plugins:maven-dependency-plugin:3.1.1:go-offline \
  --define maven.repo.local=./paciofs-obs/repository \
  --define skipTests=true

# clean source tree
mvn --projects paciofs-client clean
mvn --file ./paciofs-client/third_party/pom.xml clean initialize

tar czf ./paciofs-obs/paciofs_${paciofs_version}.orig.tar.gz --exclude '.git' ./pom.xml ./paciofs-client ./paciofs-obs/repository
