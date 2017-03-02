#!/bin/bash

# ubuntu
# check that owner group exists
if [ -z `getent group spinnaker` ]; then
  groupadd spinnaker
fi

# check that user exists
if [ -z `getent passwd spinnaker` ]; then
  useradd --gid spinnaker spinnaker -m --home-dir /home/spinnaker
fi

# install packer
PACKER_VERSION="0.12.1"
packer_version=$(packer --version)
packer_status=$?
if [ $packer_status -ne 0 ] || [ "$packer_version" -ne "$PACKER_VERSION" ]; then
  pushd .
  cd /usr/bin
  wget https://releases.hashicorp.com/packer/${PACKER_VERSION}/packer_${PACKER_VERSION}_linux_amd64.zip
  apt-get install unzip -y
  unzip -o "packer_${PACKER_VERSION}_linux_amd64.zip"
  rm "packer_${PACKER_VERSION}_linux_amd64.zip"
  popd
fi

install --mode=755 --owner=spinnaker --group=spinnaker --directory  /var/log/spinnaker/rosco
