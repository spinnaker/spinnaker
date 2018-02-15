#!/bin/bash

# Copyright 2018 Google, Inc.

# Licensed under the Apache License, Version 2.0 (the "License")
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at

#   http://www.apache.org/licenses/LICENSE-2.0

# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e
set -x

TIMESTAMP=`date +%Y%m%d%H%M`
PROVISIONED_INSTANCE=spinnaker-latest-codelab-$TIMESTAMP
CANDIDATE_CODELAB_IMAGE=spinnaker-codelab-candidate-$TIMESTAMP

# The build_google_image_functions needs ACCOUNT
# in addition to PROJECT and ZONE
ACCOUNT=$BUILDER_SERVICE_ACCOUNT
CLEAN_GOOGLE_IMAGE_SCRIPT=dev/clean_google_image.sh
EXTRACT_DISK_TO_GCS_SCRIPT=dev/build_google_image_functions.sh
source ./dev/build_google_image_functions.sh

echo "CLEAN SCRIPT '$CLEAN_GOOGLE_IMAGE_SCRIPT'"

function cleanup() {
  echo "`date`: Cleaning up"
  cleanup_instances_on_error
  delete_disk_if_exists $SOURCE_DISK
}

trap cleanup EXIT

echo "`date` Creating prototype instance"
gcloud compute instances create $PROVISIONED_INSTANCE \
    --project $PROJECT \
    --zone $ZONE \
    --image $BASE_IMAGE \
    --image-project $PROJECT \
    --machine-type n1-standard-4 \
    --scopes "storage-rw,compute-rw" \
    --no-boot-disk-auto-delete \
    --metadata block-project-ssh-keys=TRUE

PROTOTYPE_INSTANCE=$PROVISIONED_INSTANCE

echo "`date` Adding Codelab Components"
for retry in {1..20}; do
  if gcloud compute scp \
    --account $BUILDER_SERVICE_ACCOUNT \
    --project $PROJECT --zone $ZONE \
    google/codelab/first_codelab_boot.sh \
    google/codelab/provision_spinnaker_codelab_image.sh \
    $PROVISIONED_INSTANCE:.
  then
    break
  else
    sleep 2
  fi
done
# If the above failed, we'll fail below anyway so dont worry about figuring it out.


remote_commands="set -e"
remote_commands="$remote_commands; sudo \$HOME/provision_spinnaker_codelab_image.sh"
remote_commands="$remote_commands; rm ./provision_spinnaker_codelab_image.sh"
gcloud compute ssh $PROVISIONED_INSTANCE \
    --account $BUILDER_SERVICE_ACCOUNT \
    --project $PROJECT --zone $ZONE \
    --command="$remote_commands"

delete_build_instance

echo "`date` Extracting disk"

SOURCE_DISK=$PROVISIONED_INSTANCE

echo "`date` Creating cleaner instance"
CLEANER_INSTANCE="clean-$SOURCE_DISK"

ensure_empty_ssh_key
create_cleaner_instance
extract_clean_prototype_disk \
    "$SOURCE_DISK" "clean-$SOURCE_DISK" ""

image_from_prototype_disk "$CANDIDATE_CODELAB_IMAGE" "$SOURCE_DISK"

