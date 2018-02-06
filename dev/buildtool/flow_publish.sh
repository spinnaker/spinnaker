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


# Usage:  spinnaker/dev/buildtool/flow_release.sh <defaults-file>
#   e.g.  spinnaker/dev/buildtool/flow_release.sh ga-defaults.yml
#
# Note: This can be run from any directory, however root_dir path
#       in the <defaults-file> will be relative to the CWD so it
#       likely makes sense to run from the project parent directory.

set -e

# Import support functions
source $(dirname $0)/support_functions.sh

SPINNAKER_VERSION=${SPINNAKER_VERSION:-}


########################################################
# Publish all the artifacts for a spinnaker release
########################################################
function run_publish_flow() {
    start_command_unless NO_APIDOCS "publish_apidocs" \
        --spinnaker_version $SPINNAKER_VERSION

    start_command_unless NO_CHANGELOG "publish_changelog" \
        --spinnaker_version $SPINNAKER_VERSION

    start_command_unless NO_BOM "publish_spinnaker" \
        --spinnaker_version $SPINNAKER_VERSION
}


function process_args() {
  if [[ $# -lt 1 ]]; then
      >&2 echo "usage: $0 <config_file.yml> --spinnaker_version <version>"
      exit -1
  fi

  BUILDTOOL_ARGS=--default_args_file="$1"
  shift

  while [[ $# > 0 ]]
  do
      local key="$1"
      shift
      case $key in
        --no_apidocs)
          NO_APIDOCS=true
          ;;
        --no_bom)
          NO_BOM=true
          ;;
        --no_changelog)
          NO_CHANGELOG=true
          ;;
        --logs|--logs_dir)
          LOGS_DIR=$1
          shift
          ;;
        --spinnaker_version)
          SPINNAKER_VERSION=$1
          shift
          ;;
        *)
          >&2 echo "Unexpeced argument '$key'"
          exit -1
      esac
  done
}


process_args "$@"

if [[ -z $SPINNAKER_VERSION ]]; then
    echo "--spinnaker_version was not specified"
    exit -1
fi

run_publish_flow

echo "$(timestamp): FINISHED"

