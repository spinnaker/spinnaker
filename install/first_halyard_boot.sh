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

### NOTE #####################################################################
#
# This script is used for configuring Spinnaker on GCE using instance metadata 
# at boot time. It's not recommended to be used as a standalone script for
# configuring Spinnaker.
#
### NOTE #####################################################################

### FLAGS ####################################################################
# 
# The following flags can be provided as instance metadata
#  
#  gce_account (required) - name of google account to manage

#  gcr_enabled (optional) - non-empty when gcr is enabled
#  gcr_account (required for gcr) - name of gcr account to manage
#  gcr_address (required for gcr) - name of gcr address, e.g. gcr.io, eu.gcr.io
# 
#  kube_enabled (optional) - non-empty when kubernetes is enabled
#  kube_account (required for kube) - name of kubernetes account to manage
#  kube_cluster (required for kube) - name of gke cluster to manage
#  kube_zone (required for kube) - zone of gke cluster to manage
# 
#  appengine_enabled (optional) - non-empty when appengine is enabled
#  appengine_account (required for appengine) - name of appengine account to manage
#  appengine_git_https_username (required for appengine) - git username
#  appengine_git_https_password (required for appengine) - git password
# 
#
# Instance metadata is supplied to gcloud with
#
#   gcloud compute instances create <params> \
#     --metadata key1=value1,key2,value2...
#
#   NOTE! This script is typically mounted in
#         /var/spinnaker/startup/first_halyard_boot.sh, and that will need to be
#         supplied as instance metadata as well to the startup-script key.
### FLAGS ####################################################################
set -e
set -u

METADATA_URL="http://metadata.google.internal/computeMetadata/v1"
INSTANCE_METADATA_URL="$METADATA_URL/instance"
HALYARD_USER=$(cat /opt/spinnaker/config/halyard-user)
HALYARD_GROUP=$(cat /opt/spinnaker/config/halyard-user)

HAL="hal -q --log=info "

KUBE_FILE="/home/ubuntu/.kube/config"
GCR_FILE="/home/ubuntu/.gcp/gce-account.json"
GCE_FILE="/home/ubuntu/.gcp/gcr-account.json"

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
     chown $HALYARD_USER:$HALYARD_GROUP $path
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

# The following scopes must be enabled for the GCE VM that is running this script:
# All providers:
# - https://www.googleapis.com/auth/compute
# - https://www.googleapis.com/auth/devstorage.full_control
# - https://www.googleapis.com/auth/logging.write
# - https://www.googleapis.com/auth/monitoring.write
# Kubernetes or App Engine:
# - https://www.googleapis.com/auth/cloud-platform
function enable_apis() {
  gcloud service-management enable storage-api.googleapis.com

  # gcr_enabled is automatically set if either kube_enabled or appengine_enabled is set
  local gcr_enabled=$(get_instance_metadata_attribute "gcr_enabled")
  if [ -n "$gcr_enabled" ]; then
    gcloud service-management enable containerregistry.googleapis.com
    gcloud service-management enable iam.googleapis.com
  fi

  local appengine_enabled=$(get_instance_metadata_attribute "appengine_enabled")
  if [ -n "$appengine_enabled" ]; then
    gcloud service-management enable appengine.googleapis.com
  fi
}

function configure_docker() {
  local gcr_enabled=$(get_instance_metadata_attribute "gcr_enabled")

  if [ -z "$gcr_enabled" ]; then
    return 0;
  fi

  echo "Docker provider enabled"

  local config_path="$GCR_FILE"
  mkdir -p $(dirname $config_path)
  chown -R $HALYARD_USER:$HALYARD_GROUP $(dirname $config_path)

  local gcr_account=$(get_instance_metadata_attribute "gcr_account")
  local gcr_address=$(get_instance_metadata_attribute "gcr_address")

  # This service account is enabled with the Compute API.
  gcr_service_account=$(curl -s -H "Metadata-Flavor: Google" "$METADATA_URL/instance/service-accounts/default/email")

  echo "Extracting GCR credentials for email $gcr_service_account"
  gcloud iam service-accounts keys create $config_path --iam-account=$gcr_service_account

  echo "Extracted GCR credentials to $config_path"

  chmod 400 $config_path
  chown $HALYARD_USER:$HALYARD_GROUP $config_path

  $HAL config provider docker-registry enable
  $HAL config provider docker-registry account add $gcr_account \
    --password-file $config_path \
    --username _json_key \
    --address $gcr_address
}

function configure_kubernetes() {
  local kube_enabled=$(get_instance_metadata_attribute "kube_enabled")

  if [ -z "$kube_enabled" ]; then
    return 0;
  fi

  echo "Kubernetes provider enabled"

  local config_path="$KUBE_FILE"
  mkdir -p $(dirname $config_path)
  chown -R $HALYARD_USER:$HALYARD_GROUP $(dirname $config_path)

  local kube_account=$(get_instance_metadata_attribute "kube_account")
  local kube_cluster=$(get_instance_metadata_attribute "kube_cluster")
  local kube_zone=$(get_instance_metadata_attribute "kube_zone")

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
      chown $HALYARD_USER:$HALYARD_GROUP $config_path

      return 0
    else
      echo "Failed to extract kubernetes credentials to $config_path"
      rm $config_path

      return 1
    fi
  else
    echo "No kubernetes cluster specified, but kubernetes was enabled."
    echo "Aborting."
    exit 1
  fi

  local gcr_account=$(get_instance_metadata_attribute "gcr_account")

  $HAL config provider kubernetes enable
  $HAL config provider kubernetes account add $kube_account \
    --kubeconfig-path $config_path \
    --docker-registries $gcr_account
}

function configure_google() {
  local config_path="$GCE_FILE"
  mkdir -p $(dirname $config_path)
  chown -R $HALYARD_USER:$HALYARD_GROUP $(dirname $config_path)

  echo "Google provider enabled"

  local gce_account=$(get_instance_metadata_attribute "gce_account")

  local args="--project $MY_PROJECT"
  if has_instance_metadata_attribute "gce_creds"; then
    if clear_metadata_to_file "gce_creds" $config_path; then
      if [[ -s $config_path ]]; then
        args="$args --json-path $config_path"
        chmod 400 $config_path
        chown $HALYARD_USER:$HALYARD_GROUP $config_path
        echo "Successfully wrote gce credential to $config_path"
      else
        echo "Failed to write gce credential to $config_path"
        rm $config_path

        return 1
      fi
    fi
  fi

  $HAL config provider google account add $gce_account $args

  $HAL config provider google enable
}

function configure_appengine() {
  local appengine_enabled=$(get_instance_metadata_attribute "appengine_enabled")

  if [ -z "$appengine_enabled" ]; then
    return 0;
  fi

  echo "Appengine provider enabled"

  local account_name=$(get_instance_metadata_attribute "appengine_account")
  local git_https_username=$(get_instance_metadata_attribute "appengine_git_https_username")
  local git_https_password=$(get_instance_metadata_attribute "appengine_git_https_password")

  $HAL config provider appengine account add $account_name \
      --project $MY_PROJECT

  if [ -n "$git_https_password" ] && [ -n "$git_https_username" ]; then
      echo $git_https_password | $HAL config provider appengine account edit $account_name \
          --git-https-username $git_https_username \
          --git-https-password
  fi

  $HAL config provider appengine enable
}

function configure_storage() {
  $HAL config storage gcs edit --project $MY_PROJECT --bucket spinnaker-$MY_PROJECT
  $HAL config storage edit --type gcs
}

function install_spinnaker() {
  $HAL config deploy edit --type LocalDebian
  $HAL deploy apply
}

MY_ZONE=""
if full_zone=$(curl -s -H "Metadata-Flavor: Google" "$INSTANCE_METADATA_URL/zone"); then
  MY_ZONE=$(basename $full_zone)
  MY_PROJECT=$(curl -s -H "Metadata-Flavor: Google" "$METADATA_URL/project/project-id")
else
  echo "Not running on Google Cloud Platform."
  exit -1
fi

echo "Waiting for halyard to start running..."

set +e
$HAL --ready &> /dev/null

while [ "$?" != "0" ]; do
  $HAL --ready &> /dev/null
done
set -e

enable_apis
configure_docker
configure_kubernetes
configure_google
configure_appengine
configure_storage

install_spinnaker
