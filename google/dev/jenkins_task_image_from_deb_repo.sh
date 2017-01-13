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

# This script creates a Spinnaker GCP image from the Debian packages on Bintray.
#
# The following environment variables are expected to be populated:
#
# HOME [String] - Home directory for the user running this script.
#
# BUILDER_SERVICE_ACCOUNT [String] - Name of the service account used to build and store the image.
# BUILDER_JSON_CREDENTIALS [String] - Path to the credentials file for $BUILDER_SERVICE_ACCOUNT.
# BUILD_PROJECT [String] - Name of the GCP project the image is built in.
# IMAGE_NAME [String] - Name of the resulting Spinnaker image.
# IMAGE_PROJECT [String] - Name of the GCP project to publish the image to.
# LOCAL_ZONE [String] - Zone used in this script to build the image. The final resulting image is independent of any zone.
# SOURCE_IMAGE [String] - Base image to install Spinnaker onto.
#
# DEBIAN_REPO_URL [String] - Public bintray repository to install from. Defaults to the official spinnaker repository.
# BINTRAY_KEY [String] - Key to use in the case where $DEBIAN_REPO_URL points to a private repository.
# BINTRAY_USER [String] - Username to authenticate with in the case where $DEBIAN_REPO_URL points to a private repository.

# Note that this script is GCP-specific -- it only produces GCP Spinnaker images currently.
# Note also that this script expects `spinnaker/` to be checked out and in a
# subdirectory of the current working directory.
#
# A minimal Jenkins job configuration to run this script:
#
# rm -rf spinnaker/
# git clone https://github.com/spinnaker/spinnaker.git
# ./spinnaker/dev/jenkins_task_image_from_deb_repo.sh


function build_spinnaker_google_image() {
  # Normally these BINTRAY variables arent needed or used.
  # However, this can give us access to passing a private bintray DEBIAN_REPO_URL
  export BINTRAY_KEY
  export BINTRAY_USER

  spinnaker/dev/build_google_image.sh \
    --debian_repo $DEBIAN_REPO_URL \
    --image_project $BUILD_PROJECT \
    --json_credentials $BUILDER_JSON_CREDENTIALS \
    --project_id $BUILD_PROJECT \
    --source_image $SOURCE_IMAGE \
    --target_image $IMAGE_NAME \
    --update_os true \
    --zone $LOCAL_ZONE
}


function publish_image() {
  if [[ ! "$IMAGE_PROJECT" ]] || [[ "$IMAGE_PROJECT" == "$BUILD_PROJECT" ]]; then
    # Already published into the desired project
    exit 0
  fi

  spinnaker/google/dev/publish_gce_release.sh \
    --service_account $BUILDER_SERVICE_ACCOUNT \
    --original_project $BUILD_PROJECT \
    --original_image $IMAGE_NAME \
    --publish_project $IMAGE_PROJECT \
    --publish_image $IMAGE_NAME \
    --zone $LOCAL_ZONE
}

function main() {
  build_spinnaker_google_image
  publish_image
}

main
