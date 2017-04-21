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

CLEAN_GOOGLE_IMAGE_SCRIPT=${CLEAN_GOOGLE_IMAGE_SCRIPT:-"$(dirname $0)/clean_google_image.sh"}
EXTRACT_DISK_TO_GCS_SCRIPT=${EXTRACT_DISK_TO_GCS_SCRIPT:-"$(dirname $0)/extract_disk_to_gcs.sh"}
SSH_KEY_FILE=${SSH_KEY_FILE:-"$HOME/.ssh/google_empty"}


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
function extract_clean_prototype_disk() {
  local prototype_disk="$1"
  local worker_instance="$2"
  local output_file="$3"

  if [[ $output_file != "" ]]; then
    if [[ "$output_file" != gs://*.tar.gz ]]; then
      echo "$output_file is not a gs:// path to a tar.gz file"
      exit -1
    fi
  fi

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
      ${EXTRACT_DISK_TO_GCS_SCRIPT} \
      ${worker_instance}:.

  echo "`date`: Cleaning in '$worker_instance'"
  gcloud compute ssh ${worker_instance} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --ssh-key-file $SSH_KEY_FILE \
      --command="sudo ./clean_google_image.sh spinnaker"

  if [[ $output_file != "" ]]; then
    echo "`date`: Extracting disk as tar file '$output_file.'"
    gcloud compute ssh ${worker_instance} \
       --project $PROJECT \
        --account $ACCOUNT \
        --zone $ZONE \
        --ssh-key-file $SSH_KEY_FILE \
        --command="sudo ./extract_disk_to_gcs.sh spinnaker $output_file"
  fi

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

  delete_image_if_exists $target_image

  echo "`date`: Creating image '$target_image' in project '$PROJECT'"
  if [[ $prototype_disk = gs://*.tar.gz ]]; then
    echo "Creating from URI $prototype_disk"
    gcloud compute images create $target_image \
        --project $PROJECT \
        --account $ACCOUNT \
        --source-uri $prototype_disk
  else
    echo "Creating from source-disk $prototype_disk"
    gcloud compute images create $target_image \
        --project $PROJECT \
        --account $ACCOUNT \
        --source-disk $prototype_disk \
        --source-disk-zone $ZONE
  fi
}


function delete_image_if_exists() {
  local target_image="$1"

  gcloud compute images describe $target_image \
      --project $PROJECT \
      --account $ACCOUNT &> /dev/null

  if [[ $? ]]; then
    echo "`date`: Image '$target_image' does not exist yet"
  else
    echo "`date`: Deleting preexisting '$target_image' in '$PROJECT'"
    gcloud compute images delete $target_image \
      --project $PROJECT \
      --account $ACCOUNT \
      --quiet
  fi
}


function delete_build_instance() {
  echo "`date`: Cleaning up prototype instance '$PROTOTYPE_INSTANCE'"
  gcloud compute instances delete $PROTOTYPE_INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --quiet
  PROTOTYPE_INSTANCE=
}


function cleanup_instances_on_error() {
  if [[ "$PROTOTYPE_INSTANCE" != "" ]]; then
    delete_build_instance
  fi

  echo "Deleting cleaner instance '${CLEANER_INSTANCE}'"
  wait $CLEANER_INSTANCE_PID || true
  gcloud compute instances delete ${CLEANER_INSTANCE} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --quiet || true
}


function delete_prototype_disk() {
  echo "Deleting cleaner instance ${CLEANER_INSTANCE}"
  gcloud compute instances delete ${CLEANER_INSTANCE} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --quiet || true

  echo "`date`: Deleting disk '$BUILD_INSTANCE'"
  gcloud compute disks delete $BUILD_INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --quiet || true
}
