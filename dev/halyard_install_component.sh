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

# Installs Halyard and installs a single component along with spinnaker-monitoring.

set -e
set -x

RELEASE_TRACK=nightly
HALYARD_INSTALL_PARAMS=

function show_usage() {
cat <<EOF
Usage:  $0 [options]

   --component COMPONENT
       [$COMPONENT]
       The name of the Spinnaker component to install. Does not contain the 'spinnaker-' prefix.

   --version VERSION
       [$VERSION]
       The exact Spinnaker version we are baking images for.

   --halyard_config_bucket BUCKET
       Override the GCS bucket containing the global halyard repository.

   --halyard_repository
       Override the debian repository from which to get halyard itself.

   --spinnaker_repository
       Override the debian repository from which to get spinnaker artifacts.

   --spinnaker_registry
       Override the docker registery from which to get spinnaker artifacts.

   --release_track RELEASE_TRACK
       [$RELEASE_TRACK]
       Whether to install using halyard "stable" or "nightly".
EOF
}

function process_args() {
  while [[ $# > 0 ]]; do
    local key="$1"
    shift

    case $key in
        --help)
            show_usage
            exit
            ;;

        --component)
            COMPONENT=$1
            shift
            ;;

        --version)
            VERSION=$1
            shift
            ;;

        --halyard_config_bucket)
            HALYARD_INSTALL_PARAMS="$HALYARD_INSTALL_PARAMS --config-bucket $1"
            shift
            ;;

        --halyard_repository)
            HALYARD_INSTALL_PARAMS="$HALYARD_INSTALL_PARAMS --repository $1"
            shift
            ;;

        --spinnaker_repository)
            HALYARD_INSTALL_PARAMS="$HALYARD_INSTALL_PARAMS --spinnaker-repository $1"
            shift
            ;;

        --spinnaker_registry)
            HALYARD_INSTALL_PARAMS="$HALYARD_INSTALL_PARAMS --spinnaker-registry $1"
            shift
            ;;

        --release_track)
            RELEASE_TRACK=$1
            shift
            ;;
        *)
          show_usage
          >&2 echo "Unrecognized argument '$key'."
          exit -1
    esac
  done
}

EXTERNAL_ARTIFACTS=(vault-server consul-server)

function contains() {
  local e
  for e in "${@:2}"; do [[ "$e" == "$1" ]] && return 0; done
  return 1
}

function main() {
  echo "Downloading and Running Halyard Install Script..."
  wget https://raw.githubusercontent.com/spinnaker/halyard/master/install/$RELEASE_TRACK/InstallHalyard.sh
  sudo bash InstallHalyard.sh -y --user ubuntu  $HALYARD_INSTALL_PARAMS

  echo "Installing $COMPONENT and optional dependencies..."
  hal config version edit --color false --version $VERSION
  hal config deploy edit --color false --type BakeDebian

  local service_names
  if contains $COMPONENT "${EXTERNAL_ARTIFACTS[@]}"; then
    service_names=($COMPONENT)
  else
    service_names=($COMPONENT monitoring-daemon vault-client consul-client)
  fi

  echo "Installed services chosen to be ${service_names[@]}"

  hal deploy apply --color false --no-validate --service-names ${service_names[@]}
}

process_args "$@"
main
