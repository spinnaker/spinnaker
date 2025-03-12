#!/usr/bin/env bash

TEMPDIR=$(mktemp -d installhalyard.XXXX)

pushd $TEMPDIR

echo "Downloading stable halyard installer..."

curl -O https://raw.githubusercontent.com/spinnaker/halyard/master/install/Installer.sh

export RELEASE_TRACK="stable"

bash Installer.sh $@

INSTALL_EXIT=$?

popd

echo "Install finished with exit code ($INSTALL_EXIT)... cleaning up"

rm -r $TEMPDIR

rm $0
