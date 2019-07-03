#!/bin/bash

paciofs_version=$1
if [ -z "$1" ]; then
	echo "No version given, aborting"
	exit 1
fi

cd ..
mvn clean
tar czf ./paciofs-obs/paciofs_${paciofs_version}.orig.tar.gz ./pom.xml ./paciofs-fuse
