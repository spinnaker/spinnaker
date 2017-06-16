#!/bin/bash
#
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

# This script is specific to preparing a Google-hosted virtual machine
# for running Spinnaker when the instance was created with metadata
# holding configuration information, using Halyard.

set -e
set -u

METADATA_URL="http://metadata.google.internal/computeMetadata/v1"
INSTANCE_METADATA_URL="$METADATA_URL/instance"
KUBE_FILE="/home/spinnaker/.kube/config"
GCR_FILE="/home/spinnaker/.gcp/gce-account.json"
GCE_FILE="/home/spinnaker/.gcp/gcr-account.json"

function get_instance_metadata_attribute() {
  local name="$1"
  local value=$(curl -s -f -H "Metadata-Flavor: Google" \
                     $INSTANCE_METADATA_URL/attributes/$name)
  if [[ $? -eq 0 ]]; then
    echo "$value"
  else
    echo ""
  fi
}

function has_instance_metadata_attribute() {
  local value=$(get_instance_metadata_attribute $1)

  if [[ "$value" == "" ]]; then
    return 1
  else
    return 0
  fi
}

function clear_metadata_to_file() {
  local key="$1"
  local path="$2"
  local value=$(get_instance_metadata_attribute "$key")

  if [[ $value = *[![:space:]]* ]]; then
     echo "$value" > $path
     chown spinnaker:spinnaker $path
     clear_instance_metadata "$key"
     if [[ $? -ne 0 ]]; then
       die "Could not clear metadata from $key"
     fi
     return 0
  elif [[ $value != "" ]]; then
     # Clear key anyway, but act as if it werent there.
     clear_instance_metadata "$key"
  fi

  return 1
}

function clear_instance_metadata() {
  gcloud compute instances remove-metadata `hostname` \
      --zone $MY_ZONE \
      --keys "$1"
  return $?
}

function replace_startup_script() {
  # Keep the original around for reference.
  # From now on, all we need to do is start_spinnaker
  local original=$(get_instance_metadata_attribute "startup-script")
  echo "$original" > "$SPINNAKER_INSTALL_DIR/scripts/original_startup_script.sh"
  clear_instance_metadata "startup-script"
}

function configure_docker() {
  local gcr_enabled=$(get_instance_metadata_attribute "gcr_enabled")
  local gcr_account=$(get_instance_metadata_attribute "gcr_account")

  if [ -z "$gcr_enabled" ]; then
    return 0;
  fi

  local config_path="$GCR_FILE"
  mkdir -p $(dirname $config_path)
  chown -R spinnaker:spinnaker $(dirname $config_path)

  local gcr_address=$(get_instance_metadata_attribute "gcr_address")
  
  # This service account is enabled with the Compute API.
  gcr_service_account=$(curl -s -H "Metadata-Flavor: Google" "$METADATA_URL/instance/service-accounts/default/email")

  echo "Extracting GCR credentials for email $gcr_service_account"
  gcloud iam service-accounts keys create $config_path --iam-account=$gcr_service_account

  echo "Extracted GCR credentials to $config_path"

  chmod 400 $config_path
  chown spinnaker:spinnaker $config_path

  hal config provider docker-registry enable
  hal config provider docker-registry account add $gcr_account \
    --password-file $config_path \
    --username _json_key \
    --address $gcr_address
}

function configure_kubernetes() {
  local kube_enabled=$(get_instance_metadata_attribute "kube_enabled")
  local kube_account=$(get_instance_metadata_attribute "kube_account")

  if [ -z "$kube_enabled" ]; then
    return 0;
  fi

  local config_path="$KUBE_FILE"
  mkdir -p $(dirname $config_path)
  chown -R spinnaker:spinnaker $(dirname $config_path)
  
  local kube_cluster=$(get_instance_metadata_attribute "kube_cluster")
  local kube_zone=$(get_instance_metadata_attribute "kube_zone")
  local kube_config=$(get_instance_metadata_attribute "kube_config")

  if [ -n "$kube_cluster" ] && [ -n "$kube_config" ]; then
    echo "WARNING: Both \"kube_cluster\" and \"kube_config\" were supplied as instance metadata, relying on \"kube_config\""
  fi

  if [ -n "$kube_config" ]; then
    echo "Attempting to write kube_config to $config_path..."
    if clear_metadata_to_file "kube_config" $config_path; then
      # This is a workaround for difficulties using the Google Deployment Manager
      # to express no value. We'll use the value "None". But we don't want
      # to officially support this, so we'll just strip it out of this first
      # time boot if we happen to see it, and assume the Google Deployment Manager
      # got in the way.
      sed -i s/^None$//g $config_path
      if [[ -s $config_path ]]; then
        chmod 400 $config_path
        chown spinnaker $config_path
        echo "Successfully wrote kube_config to $config_path"

        return 0
      else
        echo "Failed to write kube_config to $config_path"
        rm $config_path

        return 1
      fi
    fi
  fi

  if [ -n "$kube_cluster" ]; then
    echo "Downloading credentials for cluster $kube_cluster in zone $kube_zone..."

    if [ -z "$kube_zone" ]; then
      kube_zone=$MY_ZONE
    fi

    export KUBECONFIG=$config_path
    gcloud config set container/use_client_certificate true
    gcloud container clusters get-credentials $kube_cluster --zone $kube_zone 

    if [[ -s $config_path ]]; then
      echo "Kubernetes credentials successfully extracted to $config_path"
      chmod 400 $config_path
      chown spinnaker:spinnaker $config_path

      return 0
    else
      echo "Failed to extract kubernetes credentials to $config_path"
      rm $config_path

      return 1
    fi
  fi

  hal config provider kubernetes enable
  hal config provider kubernetes account add $KUBERNETES_ACCOUNT \
    --kubeconfig-path $config_path \
    --docker-registries $DOCKER_ACCOUNT
}

function configure_google() {
  local gce_account=$(get_instance_metadata_attribute "gce_account")

  local config_path="$GCE_FILE"
  mkdir -p $(dirname $config_path)
  chown -R spinnaker:spinnaker $(dirname $config_path)

  local args="--project $MY_PROJECT"
  if has_instance_metadata_attribute "gce_creds"; then
    if clear_metadata_to_file "gce_creds" $config_path; then
      if [[ -s $config_path ]]; then
        args="$args --json-path $config_path"
        chmod 400 $config_path
        chown spinnaker $config_path
        echo "Successfully wrote gce credential to $config_path"
      else
        echo "Failed to write gce credential to $config_path"
        rm $config_path

        return 1
      fi
    fi
  fi

  hal config provider google account add $gce_account $args

  hal config provider google enable
}

function configure_storage() {
  hal config storage gcs edit --project $MY_PROJECT --bucket spinnaker-$MY_PROJECT
  hal config storage edit --type gcs
}

function configure_jenkins() {
  local jenkins_master=$(get_instance_metadata_attribute "jenkins_master")
  local jenkins_address=$(get_instance_metadata_attribute "jenkins_address")
  local jenkins_username=$(get_instance_metadata_attribute "jenkins_username")
  local jenkins_password=$(get_instance_metadata_attribute "jenkins_password")
  hal config webhook jenkins master add $jenkins_master \
    --address $jenkins_address \
    --username $jenkins_username \
    --password $jenkins_password
}

function install_spinnaker() {
  hal config deploy edit --type LocalDebian
  hal deploy apply
}

MY_ZONE=""
if full_zone=$(curl -s -H "Metadata-Flavor: Google" "$INSTANCE_METADATA_URL/zone"); then
  MY_ZONE=$(basename $full_zone)
  MY_PROJECT=$(curl -s -H "Metadata-Flavor: Google" "$METADATA_URL/project/project-id")
  MY_PROJECT_NUMBER=$(curl -s -H "Metadata-Flavor: Google" "$METADATA_URL/project/numeric-project-id")
else
  echo "Not running on Google Cloud Platform."
  exit -1
fi

configure_docker
configure_kubernetes
configure_google
configure_storage

install_spinnaker
