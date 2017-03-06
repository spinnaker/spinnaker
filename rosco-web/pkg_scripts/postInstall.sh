#!/bin/sh -e

# ubuntu
# check that owner group exists
if [ -z `getent group spinnaker` ]; then
  groupadd spinnaker
fi

# check that user exists
if [ -z `getent passwd spinnaker` ]; then
  useradd --gid spinnaker spinnaker -m --home-dir /home/spinnaker
fi

install_packer() {
  PACKER_VERSION="0.12.1"
  local packer_version=$(/usr/bin/packer --version)
  local packer_status=$?
  if [ $packer_status -ne 0 ] || [ "$packer_version" != "$PACKER_VERSION" ]; then
    TEMPDIR=$(mktemp -d installrosco.XXXX)
    cd $TEMPDIR
    wget https://releases.hashicorp.com/packer/${PACKER_VERSION}/packer_${PACKER_VERSION}_linux_amd64.zip
    unzip -o "packer_${PACKER_VERSION}_linux_amd64.zip" -d /usr/bin
    cd ..
    rm -rf $TEMPDIR
  fi
}

install_packer
install --mode=755 --owner=spinnaker --group=spinnaker --directory  /var/log/spinnaker/rosco
