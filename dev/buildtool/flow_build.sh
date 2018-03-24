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
      $EXTRA_BUILD_BOM_ARGS

  # Synchronize here so we have a bom before we continue.
  wait_for_commands_or_die "Bom"

  start_command_unless NO_CONTAINERS "build_bom_containers" \
      $EXTRA_BOM_COMMAND_ARGS
  start_command_unless NO_DEBIANS "build_debians" \
      $EXTRA_BOM_COMMAND_ARGS \
      "--max_local_builds=6"

  start_command_unless NO_HALYARD "build_halyard" \
      $EXTRA_BUILD_HALYARD_ARGS

  start_command_unless NO_CHANGELOG "build_changelog" \
      $EXTRA_BOM_COMMAND_ARGS \

  # Synchronize here so we have all the artifacts build before we continue.
  wait_for_commands_or_die "Build"

  # Only publish the bom for this build if the build succeeded.
  #
  # NOTE(ewiseblatt): 20171220
  # We probably want to publish if at least one implementation succeeded
  # (e.g. if we have VMs but not containers or vice-versa)
  # However for the time being, this is all or none. We dont want to publish
  # if nothing succeeded.
  start_command_unless NO_BOM_PUBLISH "publish_bom" \
      $EXTRA_PUBLISH_BOM_ARGS \
      $EXTRA_BOM_COMMAND_ARGS
  wait_for_commands_or_die "PublishBom"

  if [[ $NO_BOM_PUBLISH != "true" ]]; then
    # Remove the unbuilt cache, but leave it behind as what we just built.
    mv $UNBUILT_BOM_PATH ${UNVALIDATED_BOM_VERSION}.yml
  fi
}


function process_args() {
  # TODO: <bom_branch> not strictly required until --bom_branch is removed
  if [[ $# -lt 1 ]]; then
      >&2 echo "usage: $0 <config_file.yml> <bom_branch>"
      exit -1
  fi
  if [[ ! -f $1 ]]; then
      >&2 echo "Buildtool configuration '$1' does not exist."
      exit -1
  fi
  BUILDTOOL_ARGS=--default_args_file="$1"
  shift

  if [[ $# > 0 ]] && [[ $1 != --* ]]; then
      # TODO: make this required and remove --bom_branch
      BOM_BRANCH=$1
      shift
  fi

  local have_refresh=false
  while [[ $# > 0 ]]
  do
      local key="$1"
      shift
      case $key in
        --renew_bom)
          RENEW_BOM=true
          ;;
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
        --no_changelog)
          NO_CHANGELOG=true
          ;;
        --no_bom_publish)
          NO_BOM_PUBLISH=true
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
        --base_bom_path)
          have_refresh=true
          EXTRA_BUILD_BOM_ARGS="$EXTRA_BUILD_BOM_ARGS --refresh_from_bom_path $1"
          shift
          ;;
        --base_bom_version)
          have_refresh=true
          EXTRA_BUILD_BOM_ARGS="$EXTRA_BUILD_BOM_ARGS --refresh_from_bom_version $1"
          shift
          ;;
        --hal_branch)
          local branch=$1
          EXTRA_BUILD_HALYARD="$EXTRA_BUILD_HALYARD_ARGS --git_branch $1"
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

  # When we generate a bom, we're going to create $unbuilt_path
  # Then when we build off a bom, we're going to use that bom
  # Later, after the builds are done, we'll publish this with
  # the alias ${BOM_BRANCH}-latest-unvalidated
  UNBUILT_BOM_PATH=${BOM_BRANCH}-unbuilt-bom.yml
  UNVALIDATED_BOM_VERSION=${BOM_BRANCH}-latest-unvalidated

  EXTRA_PUBLISH_BOM_ARGS="--bom_alias $UNVALIDATED_BOM_VERSION"
  EXTRA_BUILD_BOM_ARGS="$EXTRA_BUILD_BOM_ARGS --git_branch $BOM_BRANCH"
  EXTRA_BUILD_BOM_ARGS="$EXTRA_BUILD_BOM_ARGS --bom_path=$UNBUILT_BOM_PATH"
  EXTRA_BOM_COMMAND_ARGS="--bom_path=$UNBUILT_BOM_PATH"

  if [[ "$BOM_BRANCH" == "" ]]; then
      >&2 echo "WARNING: Future invocations of $0 should pass the bom branch."
      return
  fi

  if [[ "$RENEW_BOM" == "true" ]]; then
    return
  fi

  local full_path="$(dirname $0)/$UNBUILT_BOM_PATH"
  if $have_refresh; then
     true  # skip
  elif [[ -f $UNBUILT_BOM_PATH ]]; then
     EXTRA_BUILD_BOM_ARGS="$EXTRA_BUILD_BOM_ARGS --refresh_from_bom_path=$UNBUILT_BOM_PATH"
     echo "WARNING: Reusing $full_path"
     echo "   This assumes a prior build failed. The bom will still refresh any changed repos,"
     echo "   but will leave unchanged repos in the bom to reuse built artifacts."
     echo "   If this is a problem, delete '$full_path'"
  else
     EXTRA_BUILD_BOM_ARGS="$EXTRA_BUILD_BOM_ARGS --refresh_from_bom_version=$UNVALIDATED_BOM_VERSION"
  fi
}


process_args "$@"
run_build_flow

echo "$(timestamp): FINISHED"
