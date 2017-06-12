#!/usr/bin/env bash
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

set -e

function show_usage() {
cat <<EOF
Usage:  $0 [options]

   --version VERSION
       [$VERSION]
       The exact Spinnaker version we are baking images for.

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

        --version)
            VERSION=$1
            shift
            ;;

        *)
          show_usage
          >&2 echo "Unrecognized argument '$key'."
          exit -1
    esac
  done
}

function setup_spinnaker_user() {
  if [[ "$homebase" == ""  ]]; then
    homebase="/home"
    echo "Setting spinnaker home to $homebase"
  fi

  if [[ -z `getent group spinnaker` ]]; then
    groupadd spinnaker
  fi

  if [[ -z `getent passwd spinnaker` ]]; then
    useradd --gid spinnaker -m --home-dir $homebase/spinnaker spinnaker
  fi
}

function main() {
  echo "Downloading and Running Halyard Install Script..."
  wget https://raw.githubusercontent.com/spinnaker/halyard/master/install/nightly/InstallHalyard.sh
  bash InstallHalyard.sh -y --user ubuntu

  rm -f InstallHalyard.sh

  echo "spinnaker" > /opt/spinnaker/config/halyard-user

  hal config version edit --version $VERSION

  hal config deploy edit --type BakeDebian

  echo "Install spinnaker subcomponents"
  hal deploy apply --service-names clouddriver deck echo front50 gate igor orca rosco redis

  mkdir -p /var/spinnaker/startup/

  echo "Installing boot script"
  wget https://raw.githubusercontent.com/spinnaker/spinnaker/master/install/first_halyard_boot.sh
  mv first_halyard_boot.sh /var/spinnaker/startup/
}

process_args "$@"

if [ -z "$VERSION" ]; then
  echo "--version is required"
  exit 1
fi

setup_spinnaker_user
main

rm -- $0
