#!/bin/bash

echo "Enter new version number (e.g. 1.0.0-1):"
read paciofs_version

echo "Creating source tarball ..."
./create_source_tarball.sh ${paciofs_version}

echo "Updating debian files ..."
./update_debian_files.sh ${paciofs_version}

echo "Deploying to OBS ..."
./deploy_paciofs.sh ${paciofs_version}

echo "Done."
