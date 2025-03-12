#!/bin/sh

# Remember to also update Dockerfile.*
KUBECTL_DEFAULT_RELEASE=1.22.17
KUBECTL_RELEASES="${KUBECTL_DEFAULT_RELEASE} 1.26.12 1.27.9 1.28.5 1.29.0"
AWS_CLI_VERSION=2.15.22
AWS_AIM_AUTHENTICATOR_VERSION=0.6.14

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
    for version in $KUBECTL_RELEASES; do
      release_version=$(echo "${version}" | cut -d. -f1,2); \
      wget -nv "https://cdn.dl.k8s.io/release/v${version}/bin/linux/amd64/kubectl" -O "/usr/local/bin/kubectl-${release_version}";
      chmod +x "/usr/local/bin/kubectl-${release_version}";
    done
    ln -sf "/usr/local/bin/kubectl-$(echo ${KUBECTL_DEFAULT_RELEASE} | cut -d. -f1,2)" /usr/local/bin/kubectl
  fi
}

install_kubectl

install --mode=755 --owner=spinnaker --group=spinnaker --directory /var/log/spinnaker/clouddriver
