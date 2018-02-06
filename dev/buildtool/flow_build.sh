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


# Usage:  spinnaker/dev/buildtool/flow_build.sh <defaults-file>
#   e.g.  spinnaker/dev/buildtool/flow_build.sh ga-defaults.yml
#
# Note: This can be run from any directory, however root_dir path
#       in the <defaults-file> will be relative to the CWD so it
#       likely makes sense to run from the project parent directory.

set -e

# Import support functions
source $(dirname $0)/support_functions.sh


########################################################
# Build all the release artifacts (wait for completion)
#
#    This flow builds all the artifacts so they can
#    be verified and validated. It does not publish any
#    of them beyond the needs for validation processes.
########################################################
function run_build_flow() {
  start_command_unless NO_BOM_BUILD "build_bom" \
      $BOM_BRANCH_ARG

  # Synchronize here so we have a bom before we continue.
  wait_for_commands_or_die "Bom"

  start_command_unless NO_CONTAINERS "build_bom_containers"
  start_command_unless NO_DEBIANS    "build_debians" "--max_local_builds=6"
  start_command_unless NO_HALYARD    "build_halyard"
  start_command_unless NO_CHANGELOG  "build_changelog"

  # Allow a lot of time because machine is starved of resources at this point
  start_command_unless NO_APIDOCS "build_apidocs" --max_wait_secs_startup=300

  # Synchronize here so we have all the artifacts build before we continue.
  wait_for_commands_or_die "Build"

  # Only publish the bom for this build if the build succeeded.
  #
  # NOTE(ewiseblatt): 20171220
  # We probably want to publish if at least one implementation succeeded
  # (e.g. if we have VMs but not containers or vice-versa)
  # However for the time being, this is all or none. We dont want to publish
  # if nothing succeeded.
  start_command_unless NO_BOM_PUBLISH "publish_bom" $BOM_ALIAS
  wait_for_commands_or_die "PublishBom"
}


function process_args() {
  if [[ $# -lt 1 ]]; then
      >&2 echo "usage: $0 <config_file.yml>"
      exit -1
  fi

  BUILDTOOL_ARGS=--default_args_file="$1"
  shift

  while [[ $# > 0 ]]
  do
      local key="$1"
      shift
      case $key in
        --no_bom_build)
          NO_BOM_BUILD=true
          ;;
        --no_containers)
          NO_CONTAINERS=true
          ;;
        --no_debians)
          NO_DEBIANS=true
          ;;
        --no_halyard)
          NO_HALYARD=true
          ;;
        --no_bom_publish)
          NO_BOM_PUBLISH=true
          ;;
        --no_changelog)
          NO_CHANGELOG=true
          ;;
        --no_apidocs)
          NO_APIDOCS=true
          ;;
        --output|--output_dir)
          OUTPUT_DIR=$1
          BUILDTOOL_ARGS="$BUILDTOOL_ARGS --output_dir=$OUTPUT_DIR"
          shift
          ;;
        --logs|--logs_dir)
          LOGS_DIR=$1
          shift
          ;;
        --bom_branch)
          local branch=$1
          BOM_BRANCH_ARG="--git_branch $1"
          BOM_ALIAS="$1-latest-unvalidated"
          shift
          ;;
        --hal_branch)
          local branch=$1
          HAL_BRANCH_ARG="--git_branch $1"
          shift
          ;;
        --parent_invocation_id)
          PARENT_INVOCATION_ID=$1
          shift
          ;;

        *)
          >&2 echo "Unexpeced argument '$key'"
          exit -1
      esac
  done
}


process_args "$@"
run_build_flow

echo "$(timestamp): FINISHED"
