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

# This script is specific to preparing a Google-hosted virtual machine
# for running Spinnaker when the instance was created with metadata
# holding configuration information.

set -e
set -u

# We're running as root, but HOME might not be defined.
AWS_DIR=/home/spinnaker/.aws
SPINNAKER_INSTALL_DIR=/opt/spinnaker
LOCAL_CONFIG_DIR=$SPINNAKER_INSTALL_DIR/config

# This status prefix provides a hook to inject output signals with status
# messages for consumers like the Google Deployment Manager Coordinator.
# Normally this isnt needed. Callers will populate it as they need
# using --status_prefix.
STATUS_PREFIX="*"

METADATA_URL="http://metadata.google.internal/computeMetadata/v1"
INSTANCE_METADATA_URL="$METADATA_URL/instance"

function write_default_value() {
  name="$1"
  value="$2"
  if egrep "^$name=" /etc/default/spinnaker > /dev/null; then
      sed -i "s/^$name=.*/$name=$value/" /etc/default/spinnaker
  else
      echo "$name=$value" >> /etc/default/spinnaker
  fi
}

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
     chown spinnaker:spinnaker $path
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
  clear_instance_metadata "startup-script"
}

function extract_spinnaker_local_yaml() {
  local value=$(get_instance_metadata_attribute "spinnaker_local")
  if [[ "$value" == "" ]]; then
    return 0
  fi

  local yml_path=$LOCAL_CONFIG_DIR/spinnaker-local.yml
  sudo cp $SPINNAKER_INSTALL_DIR/config/default-spinnaker-local.yml $yml_path
  chown spinnaker:spinnaker $yml_path
  chmod 600 $yml_path

  mkdir -p $AWS_DIR
  chown -R spinnaker:spinnaker /home/spinnaker
  
  PYTHONPATH=$SPINNAKER_INSTALL_DIR/pylib python \
      $SPINNAKER_INSTALL_DIR/pylib/spinnaker/transform_old_config.py \
      "$value" /etc/default/spinnaker $yml_path $AWS_DIR/credentials

  # Be extra sure we're protecting this
  chown spinnaker:spinnaker $yml_path
  chmod 600 $yml_path

  if [[ -f $AWS_DIR/credentials ]]; then
      chown spinnaker:spinnaker $AWS_DIR/credentials
      chmod 600 $AWS_DIR/credentials
  fi

  clear_instance_metadata "spinnaker_local"
  return 0
}

function extract_spinnaker_credentials() {
    extract_spinnaker_google_credentials
    extract_spinnaker_aws_credentials
}

function extract_spinnaker_google_credentials() {
  local json_path="$LOCAL_CONFIG_DIR/google-credentials.json"
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
      chown spinnaker $json_path
      echo "Extracted google credentials to $json_path"
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
  if [[ -f "$LOCAL_CONFIG_DIR/spinnaker-local.yml" ]]; then
      sed -i "s/\( \+jsonPath:\).\+/\1 ${json_path//\//\\\/}/g" \
          $LOCAL_CONFIG_DIR/spinnaker-local.yml
  fi
}

function extract_spinnaker_aws_credentials() {
  local credentials_path="$AWS_DIR/credentials"
  mkdir -p $(dirname $credentials_path)
  if clear_metadata_to_file "aws_credentials" $credentials_path; then
    # This is a workaround for difficulties using the Google Deployment Manager
    # to express no value. We'll use the value "None". But we dont want
    # to officially support this, so we'll just strip it out of this first
    # time boot if we happen to see it, and assume the Google Deployment Manager
    # got in the way.
    sed -i s/^None$//g $credentials_path
    if [[ -s $credentials_path ]]; then
      chmod 600 $credentials_path
      chown spinnaker:spinnaker $credentials_path
      echo "Extracted aws credentials to $credentials_path"
    else
       rm $credentials_path
    fi
    write_default_value "SPINNAKER_AWS_ENABLED" "true"
  else
    clear_instance_metadata "aws_credentials"
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

if full_zone=$(curl -s -H "Metadata-Flavor: Google" "$INSTANCE_METADATA_URL/zone"); then
  MY_ZONE=$(basename $full_zone)
  MY_PROJECT=$(curl -s -H "Metadata-Flavor: Google" "$METADATA_URL/project/project-id")

  write_default_value "SPINNAKER_GOOGLE_ENABLED" "true"
  write_default_value "SPINNAKER_GOOGLE_PROJECT_ID" "$MY_PROJECT"
  write_default_value "SPINNAKER_GOOGLE_DEFAULT_ZONE" "$MY_ZONE"
  write_default_value "SPINNAKER_GOOGLE_DEFAULT_REGION" "${MY_ZONE%-*}"
else
  echo "Not running on Google Cloud Platform."
  exit -1
  MY_ZONE=""
fi

# There appears to be a race condition within cassandra starting thrift while
# we perform the dist-upgrade below. So we're going to try working around
# that by waiting for cassandra to get past its thrift initialization.
# Otherwise, we'd prefer to upgrade first, then wait for cassandra to
# be ready for JMX (7199).
if ! nc -z localhost 9160; then
    echo "Waiting for Cassandra to start..."
    while ! nc -z localhost 9160; do
       sleep 1
    done
    echo "Cassandra is ready."
fi

# Apply outstanding OS updates since time of image creation
# but keep spinnaker version itself intact only during forced dist-upgrade
apt-mark hold spinnaker-clouddriver spinnaker-deck spinnaker-echo spinnaker-front50 spinnaker-gate spinnaker-igor spinnaker-orca spinnaker-rosco spinnaker-rush spinnaker
apt-get -y update
apt-get -y dist-upgrade
apt-mark unhold spinnaker-clouddriver spinnaker-deck spinnaker-echo spinnaker-front50 spinnaker-gate spinnaker-igor spinnaker-orca spinnaker-rosco spinnaker-rush spinnaker
sed -i "s/start_rpc: false/start_rpc: true/" /etc/cassandra/cassandra.yaml
while ! $(nodetool enablethrift >& /dev/null); do
    sleep 1
    echo "Retrying..."
done


process_args

echo "$STATUS_PREFIX  Extracting Configuration Info"
extract_spinnaker_local_yaml

echo "$STATUS_PREFIX  Extracting Credentials"
extract_spinnaker_credentials

echo "$STATUS_PREFIX  Configuring Spinnaker"
$SPINNAKER_INSTALL_DIR/scripts/reconfigure_spinnaker.sh


# Replace this first time boot with the normal startup script
# that just starts spinnaker (and its dependencies) without configuring anymore.
echo "$STATUS_PREFIX  Cleaning Up"
replace_startup_script

echo "$STATUS_PREFIX  Restarting Spinnaker"
stop spinnaker
start spinnaker
echo "$STATUS_PREFIX  Spinnaker is now configured"
