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

CLEAN_GOOGLE_IMAGE_SCRIPT="$(dirname $0)/clean_google_image.sh"
SSH_KEY_FILE=$HOME/.ssh/google_empty


function create_empty_ssh_key() {
  # https://cloud.google.com/compute/docs/instances/adding-removing-ssh-keys
  if [[ ! -f ~/.ssh/google_empty.pub ]]; then
    echo "Creating ~/.ssh/google_empty SSH key"
    ssh-keygen -N "" -t rsa -f ~/.ssh/google_empty -C $USER
    sed "s/^ssh-rsa/$USER:ssh-rsa/" -i ~/.ssh/google_empty.pub
  fi
}


# Clean PROTOTYPE_DISK by attaching it to an existing WORKER_INSTANCE.
# This will attach/detach the disk but leave WORKER_INSTANCE running.
function clean_prototype_disk() {
  local prototype_disk="$1"
  local worker_instance="$2"

  echo "`date`: Preparing '$worker_instance'"
  gcloud compute instances add-metadata $worker_instance \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --metadata-from-file ssh-keys=$HOME/.ssh/google_empty.pub

  gcloud compute instances attach-disk ${worker_instance} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --disk $prototype_disk \
      --device-name spinnaker
      
  gcloud compute copy-files \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --ssh-key-file $SSH_KEY_FILE \
      ${CLEAN_GOOGLE_IMAGE_SCRIPT} \
      ${worker_instance}:.

  echo "`date`: Cleaning in '$worker_instance'"
  gcloud compute ssh ${worker_instance} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --ssh-key-file $SSH_KEY_FILE \
      --command="sudo ./clean_google_image.sh spinnaker"

  gcloud compute instances detach-disk ${worker_instance} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --disk $prototype_disk
  echo "`date`: Finished cleaning disk '$prototype_disk'."
}


# Create TARGET_IMAGE from PROTOTYPE_DISK.
# This will leave the disk instact
function image_from_prototype_disk() {
  local target_image="$1"
  local prototype_disk="$2"

  echo "`date`: Creating image '$target_image' in project '$PROJECT'"
  gcloud compute images create $target_image \
      --project $PROJECT \
      --account $ACCOUNT \
      --source-disk $prototype_disk \
      --source-disk-zone $ZONE
}

