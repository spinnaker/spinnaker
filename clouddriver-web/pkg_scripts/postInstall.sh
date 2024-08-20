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

install_awscli2() {
  if [ -d "/usr/local/aws-cli/v2/${AWS_CLI_VERSION}" ]; then
    echo "awscli2 ${AWS_CLI_VERSION} is already installed"
  else
    echo "Installing awscli2 ${AWS_CLI_VERSION}"
    curl -s "https://awscli.amazonaws.com/awscli-exe-linux-x86_64-${AWS_CLI_VERSION}.zip" -o "awscliv2.zip"

    # This shouldn't usually exist unless the installation failed previously
    if [ -d "./aws" ]; then
      rm -rf ./aws
    fi

    unzip awscliv2.zip
    ./aws/install --update
    rm -rf ./awscliv2.zip ./aws
  fi

  echo "Installing aws-iam-authenticator ${AWS_AIM_AUTHENTICATOR_VERSION}"
  curl -s "https://github.com/kubernetes-sigs/aws-iam-authenticator/releases/download/v${AWS_AIM_AUTHENTICATOR_VERSION}/aws-iam-authenticator_${AWS_AIM_AUTHENTICATOR_VERSION}_linux_amd64" -o aws-iam-authenticator
  chmod +x ./aws-iam-authenticator
  mv ./aws-iam-authenticator /usr/local/bin/aws-iam-authenticator
}

install_kubectl
install_awscli2

install --mode=755 --owner=spinnaker --group=spinnaker --directory /var/log/spinnaker/clouddriver
