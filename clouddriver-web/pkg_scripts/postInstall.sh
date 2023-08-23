#!/bin/sh

# Remember to also update Dockerfile.*
# KUBECTL_RELEASE kept one minor version behind latest to maximise compatibility overlap
KUBECTL_RELEASE=1.22.17

# ubuntu
# check that owner group exists
if [ -z "$(getent group spinnaker)" ]; then
  groupadd spinnaker
fi

# check that user exists
if [ -z "$(getent passwd spinnaker)" ]; then
  useradd --gid spinnaker spinnaker -m --home-dir /home/spinnaker
fi

install_kubectl() {
  if [ -z "$(which kubectl)" ]; then
    wget "https://storage.googleapis.com/kubernetes-release/release/v${KUBECTL_RELEASE}/bin/linux/amd64/kubectl"
    chmod +x kubectl
    mv ./kubectl /usr/local/bin/kubectl
  fi
}

install_kubectl

install --mode=755 --owner=spinnaker --group=spinnaker --directory /var/log/spinnaker/clouddriver
