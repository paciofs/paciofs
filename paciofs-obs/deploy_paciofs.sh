#!/bin/bash

paciofs_version=$1
if [ -z "$1" ]; then
	echo "No version given, aborting"
	exit 1
fi

cp ./paciofs.dsc ./home\:robert-schmidtke\:paciofs/paciofs
cp ./debian.changelog ./home\:robert-schmidtke\:paciofs/paciofs
cp ./debian.control ./home\:robert-schmidtke\:paciofs/paciofs
cp ./debian.rules ./home\:robert-schmidtke\:paciofs/paciofs
cp ./paciofs_${paciofs_version}.orig.tar.gz ./home\:robert-schmidtke\:paciofs/paciofs

cd ./home\:robert-schmidtke\:paciofs/paciofs
osc add ./paciofs.dsc
osc add ./debian.changelog
osc add ./debian.control
osc add ./debian.rules
osc add ./paciofs_${paciofs_version}.orig.tar.gz

osc commit -m "PacioFS ${paciofs_version}" \
  ./paciofs.dsc ./debian.changelog ./debian.control ./debian.rules ./paciofs_${paciofs_version}.orig.tar.gz
