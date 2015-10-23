#!/bin/bash
#
# Copyright 2015 Google Inc. All Rights Reserved.
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

set -e
set -u

# We're running as root, but HOME might not be defined.
HOME=${HOME:-"/root"}
SPINNAKER_INSTALL_DIR=/opt/spinnaker
CONFIG_DIR=$HOME/.spinnaker

# This status prefix provides a hook to inject output signals with status
# messages for consumers like the Google Deployment Manager Coordinator.
# Normally this isnt needed. Callers will populate it as they need
# using --status_prefix.
STATUS_PREFIX="*"

METADATA_URL="http://metadata.google.internal/computeMetadata/v1"
INSTANCE_METADATA_URL="$METADATA_URL/instance"
if full_zone=$(curl -s -H "Metadata-Flavor: Google" "$INSTANCE_METADATA_URL/zone"); then
  MY_ZONE=$(basename $full_zone)
else
  echo "Not running on Google Cloud Platform."
  MY_ZONE=""
fi

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

function write_instance_metadata() {
  gcloud compute instances add-metadata `hostname` \
      --zone $MY_ZONE \
      --metadata "$@"
  return $?
}

function clear_metadata_to_file() {
  local key="$1"
  local path="$2"
  local value=$(get_instance_metadata_attribute "$key")

  if [[ "$value" != "" ]]; then
     echo "$value" > $path
     clear_instance_metadata "$key"
     if [[ $? -ne 0 ]]; then
       die "Could not clear metadata from $key"
     fi
     return 0
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
  write_instance_metadata \
      "startup-script=$SPINNAKER_INSTALL_DIR/scripts/start_spinnaker.sh"
}

# This is to support old style configuration only
# (which is still the default)
function extract_spinnaker_config() {
  local config="$CONFIG_DIR/spinnaker_config.cfg"
  local value=$(get_instance_metadata_attribute "spinnaker_config")
  if [[ "$value" == "" ]]; then
    echo "WARNING: Failed to write $config. Using defaults."
    cp "$SPINNAKER_INSTALL_DIR/config_templates/default_spinnaker_config.cfg" \
        "$config"
    return
  fi

  mkdir -p $(dirname $config)

  # This is a workaround for difficulties using the Google Deployment Manager
  # to express no value. We'll use the value "None". But we dont want
  # to officially support this, so we'll just strip it out of this first
  # time boot if we happen to see it, and assume the Google Deployment Manager
  echo "${value//=None/=}" > $config
  chmod 600 $config

  clear_instance_metadata "spinnaker_config"
  echo "Wrote spinnaker_config"
}

function extract_spinnaker_local_yaml() {
  local value=$(get_instance_metadata_attribute "spinnaker_local")
  if [[ "$value" == "" ]]; then
    return 1
  fi

  local config="$CONFIG_DIR/spinnaker-local.yml"
  mkdir -p $(dirname $config)
  echo "$value" > $config
  chmod 600 $config

  clear_instance_metadata "spinnaker_local"
  return 0
}

function extract_spinnaker_credentials() {
  local json_path="$CONFIG_DIR/ManagedProjectCredentials.json"
  mkdir -p $(dirname $json_path)
  if clear_metadata_to_file "managed_project_credentials" $json_path; then
    # This is a workaround for difficulties using the Google Deployment Manager
    # to express no value. We'll use the value "None". But we dont want
    # to officially support this, so we'll just strip it out of this first
    # time boot if we happen to see it, and assume the Google Deployment Manager
    # got in the way.
    sed -i s/^None$//g $json_path
    if [[ -s $json_path ]]; then
      chmod 400 $json_path
    else
       rm $json_path
    fi
  else
    clear_instance_metadata "managed_project_credentials"
    json_path=""
  fi

  # This cant be configured when we create the instance because
  # the path is local within this instance (file transmitted in metadata)
  # Remove the old line, if one existed, and replace it with a new one.
  # This way it does not matter whether the user supplied it or not
  # (and might have had it point to something client side).
  if [[ -f "$CONFIG_DIR/spinnaker-local.yml" ]]; then
      sed -i "s/\( \+jsonPath:\).\+/\1 ${json_path//\//\\\/}/g" \
          $CONFIG_DIR/spinnaker-local.yml
  fi
  if [[ -f "$CONFIG_DIR/spinnaker_config.cfg" ]]; then
      # DEPRECATED
      sed -i -e '/^GOOGLE_PRIMARY_JSON_CREDENTIAL_PATH=/d' \
          "$CONFIG_DIR/spinnaker_config.cfg"
      echo "GOOGLE_PRIMARY_JSON_CREDENTIAL_PATH=$json_path" \
          >> "$CONFIG_DIR/spinnaker_config.cfg"
  fi
}

function process_args() {
  while [[ $# > 0 ]]
  do
    local key="$1"
    case $key in
    --status_prefix)
      STATUS_PREFIX="$2"
      shift
      ;;

    *)
      echo "ERROR: unknown option '$key'."
      exit -1
      ;;
    esac
    shift
  done
}

# apply outstanding updates since time of image creation
apt-get -y update
apt-get -y dist-upgrade

process_args
mkdir -p /root/.spinnaker

echo "$STATUS_PREFIX  Extracting Configuration Info"
if ! extract_spinnaker_local_yaml; then
  # DEPRECATED
  extract_spinnaker_config
fi

echo "$STATUS_PREFIX  Extracting Credentials"
extract_spinnaker_credentials

echo "$STATUS_PREFIX  Configuring Spinnaker"
$SPINNAKER_INSTALL_DIR/scripts/reconfigure_spinnaker.sh


# Replace this first time boot with the normal startup script
# that just starts spinnaker (and its dependencies) without configuring anymore.
echo "$STATUS_PREFIX  Cleaning Up"
replace_startup_script

echo "$STATUS_PREFIX  Starting Spinnaker"
$SPINNAKER_INSTALL_DIR/scripts/start_spinnaker.sh
echo "$STATUS_PREFIX  Spinnaker is now ready"
