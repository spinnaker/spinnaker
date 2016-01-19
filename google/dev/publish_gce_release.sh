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

# Publishes a spinnaker release from one project into another project.
# The release consists of a GCE image and possibly a directory containing
# the artifacts for consumption without the image (e.g. to deploy somewhere
# other than GCE or without the release image).
#
# Sample usage:
#   ./publish_gce_release.sh --original_image $ORIGINAL_IMAGE --original_project $ORIGINAL_PROJECT --publish_image $PUBLISH_IMAGE --publish_project $PUBLISH_PROJECT --original_repo $ORIGINAL_ARTIFACT_REPO_PATH --publish_repo $PUBLISH_ARTIFACT_REPO_PATH
#
#   Use --service_account to specify which service account to use when publishing
#   to give permission to create the image in the target project (and also read from
#   the source project). The service account must already be added to gcloud with
#   gcloud auth activate-service-account.

set -e
set -u

SERVICE_ACCOUNT=${SERVICE_ACCOUNT:-""}
ORIGINAL_ARTIFACT_REPO_PATH=${ORIGINAL_ARTIFACT_REPO_PATH:-""}
ORIGINAL_IMAGE=${ORIGINAL_IMAGE:-""}
ORIGINAL_PROJECT_ID=${ORIGINAL_PROJECT_ID:-$(gcloud config list  \
                                      | grep "project = " \
                                      | sed 's/project = //g')}

PUBLISH_ARTIFACT_REPO_PATH=${PUBLISH_ARTIFACT_REPO_PATH:-""}
PUBLISH_IMAGE=${PUBLISH_PROJECT_ID:-""}
PUBLISH_PROJECT_ID=${PUBLISH_PROJECT_ID:-""}
ZONE=${ZONE:-"us-central1-c"}


function die_if_empty() {
  if [[ "$1" == "" ]]; then
    >&2 echo "ERROR: Missing required argument $2."
    exit -1
  fi
}

function validate_args() {
  die_if_empty "$PUBLISH_IMAGE" "--publish_image"
  die_if_empty "$PUBLISH_PROJECT_ID" "--publish_project_id"
  die_if_empty "$ORIGINAL_IMAGE" "--original_image"
  die_if_empty "$ORIGINAL_PROJECT_ID" "--original_project_id"
  die_if_empty "$ZONE" "--zone"

  
  if [[ "$PUBLISH_ARTIFACT_REPO_PATH" && ! "$ORIGINAL_ARTIFACT_REPO_PATH" ]] \
      || [[ ! "$PUBLISH_ARTIFACT_REPO_PATH" \
           && "$ORIGINAL_ARTIFACT_REPO_PATH" ]]; then
      >&2 echo "Either both --publish_repo and --original_repo" \
               "or neither should be provided."
      exit -1
  fi
  if [[ "$ORIGINAL_ARTIFACT_REPO_PATH" ]]; then
    if [[ ! -f "$ORIGINAL_ARTIFACT_REPO_PATH" ]] \
       && ! gsutil ls "$ORIGINAL_ARTIFACT_REPO_PATH" >& /dev/null; then
       >&2 echo "Cannot access $ORIGINAL_ARTIFACT_REPO_PATH"
    fi
  fi
}

function process_args() {
  while [[ $# > 0 ]]
  do
      local key="$1"
      shift
      case $key in
         --service_account)
           SERVICE_ACCOUNT="$1"
           shift
           ;;
         --publish_repo)
           PUBLISH_ARTIFACT_REPO_PATH="$1"
           shift
           ;;
         --publish_image)
           PUBLISH_IMAGE="$1"
           shift
           ;;
         --publish_project)
           PUBLISH_PROJECT_ID="$1"
           shift
           ;;
         --original_repo)
           ORIGINAL_ARTIFACT_REPO_PATH="$1"
           shift
           ;;
         --original_image)
           ORIGINAL_IMAGE="$1"
           shift
           ;;
         --original_project)
           ORIGINAL_PROJECT_ID="$1"
           shift
           ;;
         --zone)
           ZONE="$1"
           shift
           ;;
         *)
           echo "ERROR: Unknown argument '$key'"
           exit -1
      esac
  done
}

function copy_artifact_repository() {
  from="$1"
  to="$2"

  if [[ "$to" =~ gs://.* ]]; then
    # Ensure GCS bucket exists
    gs_path=${to#gs://}
    gs_bucket=$(echo "$gs_path" | sed 's/\([^\/]*\)\/.*/\1/g')
    gsutil mb -p $PUBLISH_PROJECT_ID "gs://$gs_bucket" >& /dev/null  || true
  else
    # Ensure target directory exists
    mkdir -p $(dirname $to)
    echo "DIR $to"
  fi

  # This doesnt work
  # gsutil -q -m cp -R "$ORIGINAL_ARTIFACT_REPO_PATH/." \
  #    "$PUBLISH_ARTIFACT_REPO_PATH"
  # To ensure the directory exists, we'll add a dummy
  # file to force its creation, then remove it when we're done.
  # Otherwise we'll copy a nested directory, which is not our intention.
  temp=$(mktemp)
  gsutil -q cp $temp "$PUBLISH_ARTIFACT_REPO_PATH/__placeholder__"
  rm $temp
  gsutil -q -m cp -R "$ORIGINAL_ARTIFACT_REPO_PATH/*" \
        "$PUBLISH_ARTIFACT_REPO_PATH"
  gsutil -q rm "$PUBLISH_ARTIFACT_REPO_PATH/__placeholder__"
}


process_args "$@"
validate_args

GCLOUD_ACCOUNT_ARG=""
if [[ "$SERVICE_ACCOUNT" ]]; then
  GCLOUD_ACCOUNT_ARG="--account $SERVICE_ACCOUNT"
fi

echo "Creating disk"
gcloud compute disks create "$PUBLISH_IMAGE" $GCLOUD_ACCOUNT_ARG\
    --project "$PUBLISH_PROJECT_ID" \
    --zone "$ZONE" \
    --image-project "$ORIGINAL_PROJECT_ID" \
    --image "$ORIGINAL_IMAGE"

echo "Publishing image"
gcloud compute images create "$PUBLISH_IMAGE" $GCLOUD_ACCOUNT_ARG\
    --project "$PUBLISH_PROJECT_ID" \
    --source-disk-zone "$ZONE" \
    --source-disk "$PUBLISH_IMAGE"

echo "Deleting disk"
gcloud compute disks delete "$PUBLISH_IMAGE" $GCLOUD_ACCOUNT_ARG\
    --project "$PUBLISH_PROJECT_ID" \
    --zone "$ZONE" \
    --quiet

if [[ "$PUBLISH_ARTIFACT_REPO_PATH" ]]; then
  echo "Copying artifact repository"
  copy_artifact_repository \
      "$ORIGINAL_ARTIFACT_REPO_PATH" "$PUBLISH_ARTIFACT_REPO_PATH" 
fi

echo "Published $PUBLISH_IMAGE to $PUBLISH_PROJECT_ID."
