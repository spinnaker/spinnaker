#!/bin/bash

# Copyright 2017 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# The purpose of this script is to install Halyard at the latest released
# version (output from our build process, not a Github release) and use
# Halyard to deploy Spinnaker to Kubernetes at the versions specified in
# the latest nightly version. After Spinnaker is instantiated on Kubernetes,
# a Citest suite will run against it. This is outside the scope of this script.

# It is assumed that this script will run in a container with the proper
# environment variables and files present.
#
# The container expects the following environment variables to be set.
# These should be handled by creating k8s secrets and specifying
# environment variables in the pod spec using those secrets.
# ---
# Parameters of the GCP project Spinnaker is maintaining.
# BUILD_PROJECT
# BUILD_KEY # json keyfile contents
#
# Parameters to configure the Docker registry.
# DOCKER_KEY # json keyfile contents
#
# Kubernetes config in json format so we can pass it to the
# container an an env variable.
# KUBE_CONF
#
# Name of the GCS bucket used to store Spinnaker's persistent data.
# GCS_BUCKET
#
# Parameters to configure Jenkins to talk to Spinnaker.
# JENKINS_ADDRESS
# JENKINS_USERNAME
# JENKINS_PASSWORD

set -e
export TERMINFO=/usr/lib/terminfo
export TERM=xterm

mkdir /supporting_data
echo $BUILD_KEY >> /supporting_data/build.json
echo $DOCKER_KEY >> /supporting_data/docker.json

curl -O https://storage.googleapis.com/kubernetes-release/release/v1.5.4/bin/linux/amd64/kubectl
chmod +x kubectl
mv kubectl /usr/local/bin/kubectl

echo "Downloading Halyard..."
wget https://raw.githubusercontent.com/spinnaker/halyard/master/install/nightly/InstallHalyard.sh

# We need to make changes to the Halyard install script so it can run in a container.
sed "s/^printf/echo/" -i InstallHalyard.sh
sed "s/^start halyard/exit 0/" -i InstallHalyard.sh

sudo bash InstallHalyard.sh -y
echo "Starting Halyard..."
sudo /opt/halyard/bin/halyard 2>&1 /var/log/spinnaker/halyard/halyard.log &
sleep 60

echo "Configuring k8s..."
mkdir ~/.kube
echo $KUBE_CONF >> ~/.kube/config

echo "Running Halyard commands to deploy spinnaker."
cat /supporting_data/build.json
cat /supporting_data/docker.json
hal config version edit --version nightly

hal config provider docker-registry enable
hal config provider docker-registry account add my-gcr-account \
    --password-file /supporting_data/docker.json --username _json_key \
    --address gcr.io

hal config provider kubernetes enable
hal config provider kubernetes account add my-k8s-account \
    --docker-registries my-gcr-account

hal config provider google enable
hal config provider google account add my-gce-account \
    --json-path /supporting_data/build.json --project $BUILD_PROJECT

hal config ci jenkins enable
echo $JENKINS_PASSWORD | hal config ci jenkins master add jenkins --address $JENKINS_ADDRESS --username $JENKINS_USERNAME --password

# Uses default root-folder 'spinnaker' implicitly.
hal config storage gcs edit --json-path /supporting_data/build.json \
    --project $BUILD_PROJECT --bucket $GCS_BUCKET
# Enable GCS.
hal config storage edit --type gcs

hal config deploy edit --type distributed --account-name my-k8s-account
hal deploy apply

cat ~/.hal/default/install.sh
