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

set -e

source $(dirname $0)/build_google_image_functions.sh

ADD_CLOUD_LOGGING=true
ADD_MONITORING=true
UPDATE_OS=true

TIME_DECORATOR=$(date +%Y%m%d%H%M%S)
DEBIAN_REPO_URL=https://dl.bintray.com/spinnaker/debians
INSTALL_SCRIPT=https://dl.bintray.com/spinnaker/scripts/InstallSpinnaker.sh

BASE_IMAGE_OR_FAMILY=ubuntu-1404-lts
BASE_IMAGE_OR_FAMILY_PROJECT=
TARGET_IMAGE=${USER}-spinnaker-${TIME_DECORATOR}

DEFAULT_PROJECT=$(gcloud config list 2>&1 \
                  | grep "project =" | head -1 \
                  | sed "s/.* \(.*\)$/\1/")
DEFAULT_ACCOUNT=$(gcloud auth list 2>&1 \
                  | grep ACTIVE | head -1 \
                  | sed "s/.* \(.*\) ACTIVE/\1/")


INSTANCE=
INSTANCE_CLEANER=
ACCOUNT=$DEFAULT_ACCOUNT
PROJECT=$DEFAULT_PROJECT
ZONE=$(gcloud config list 2>&1 \
           | grep "zone =" | head -1 \
           | sed "s/.* \(.*\)$/\1/")



function fix_defaults() {
  if [[ "$ZONE" == "" ]]; then
    ZONE=us-central1-f
  fi

  local image_entry=$(gcloud compute images list 2>&1 \
                      | grep $BASE_IMAGE_OR_FAMILY | head -1)

  # If this was a family, convert it to a particular image for argument consistency
  BASE_IMAGE_OR_FAMILY=$(echo "$image_entry" | sed "s/\([^ ]*\) .*/\1/")
  if [[ "$BASE_IMAGE_OR_FAMILY_PROJECT" == "" ]]; then
    BASE_IMAGE_OR_FAMILY_PROJECT=$(echo "$image_entry" | sed "s/[^ ]* *\([^ ]*\)* .*/\1/")
  fi

  if [[ "$INSTANCE" == "" ]]; then
    INSTANCE="build-${TARGET_IMAGE}-${TIME_DECORATOR}"
  fi
  INSTANCE_CLEANER="clean-${TARGET_IMAGE}-${TIME_DECORATOR}"
}


function show_usage() {
    fix_defaults

cat <<EOF
Usage:  $0 [options]

   --install_script INSTALL_SCRIPT
       [$INSTALL_SCRIPT]
       The path or URL to the install script to use.

   --no_update_os
       Do not force an upgrade-dist of the base OS.

   --account ACCOUNT
       [$ACCOUNT]
       Use this gcloud account to build the image.

   --project PROJECT
       [$PROJECT]
       Publish (and build) the image in the PROJECT id.

   --zone ZONE
       [$ZONE]
       Zone to use when building the image. The final image is global.

   --debian_repo DEBIAN_REPO_URL
       [$DEBIAN_REPO_URL]
       Use the DEBIAN_REPO_URL to obtain the Spinnaker packages.

   --base_image BASE_IMAGE_OR_FAMILY
       [$BASE_IMAGE_OR_FAMILY]
       Use BASE_IMAGE_OR_FAMILY as the base image.

   --target_image TARGET_IMAGE
       [$TARGET_IMAGE]
       Produce the given TARGET_IMAGE.
   
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

        --install_script)
            INSTALL_SCRIPT=$1
            shift
            ;;

        --no_update_os)
            UPDATE_OS=false
            ;;

        --debian_repo)
            DEBIAN_REPO_URL=$1
            shift
            ;;

        --base_image)
            BASE_IMAGE_OR_FAMILY=$1
            shift
            ;;

        --target_image)
            TARGET_IMAGE=$1
            shift
            ;;

        --account)
            ACCOUNT=$1
            shift
            ;;

        --project)
            PROJECT=$1
            shift
            ;;
        --project_id)
            # deprecated
            PROJECT=$1
            shift
            ;;
        --image_project)
            2>& echo "--image_project is no longer used -- ignoring."
            shift
            ;;
        --json_credentials)
            2>& echo "--json_credentials is no longer used -- ignoring.  Use --account instead"
            shift
            ;;

        --zone)
            ZONE=$1
            shift
            ;;

        *)
          show_usage
          >&2 echo "Unrecognized argument '$key'."
          exit -1
    esac
  done
}


function delete_build_instance() {
  echo "`date`: Cleaning up prototype instance '$INSTANCE'"
  gcloud compute instances delete $INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --quiet
}


function cleanup_instances_on_error() {
  delete_build_instance

  echo "Deleting cleaner instance ${INSTANCE_CLEANER}"
  gcloud compute instances delete ${INSTANCE_CLEANER} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --quiet || true
}


function delete_prototype_disk() {
  echo "Deleting cleaner instance ${INSTANCE_CLEANER}"
  gcloud compute instances delete ${INSTANCE_CLEANER} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --quiet || true
  
  echo "`date`: Deleting disk '$INSTANCE'"
  gcloud compute disks delete $INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --quiet || true
}


function create_prototype_disk() {
  echo "`date`: Fetching install script from $INSTALL_SCRIPT"
  local install_script_path

  if [[ -f "$INSTALL_SCRIPT" ]]; then
     install_script_path="$INSTALL_SCRIPT"
  else
    curl -sS $INSTALL_SCRIPT -o /tmp/install-spinnaker-${TIME_DECORATOR}.sh
    install_script_path=/tmp/install-spinnaker-${TIME_DECORATOR}.sh
    chmod +x $install_script_path
  fi

  echo "`date`: Creating prototype instance '$INSTANCE'"
  gcloud compute instances create $INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --machine-type n1-standard-1 \
      --boot-disk-type pd-ssd \
      --image $BASE_IMAGE_OR_FAMILY \
      --image-project $BASE_IMAGE_OR_FAMILY_PROJECT \
      --metadata block-project-ssh-keys=TRUE

  trap cleanup_instances_on_error EXIT

  echo "`date` Adding ssh key to '$INSTANCE'"
  gcloud compute instances add-metadata $INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --metadata-from-file ssh-keys=$HOME/.ssh/google_empty.pub

  # This second instance will be used later to clean the image
  # we dont need it yet, but will spin it up now to have it ready.
  echo "`date` Warming up '$INSTANCE_CLEANER' for later"
  (gcloud compute instances create ${INSTANCE_CLEANER} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --machine-type n1-standard-1 \
      --image $BASE_IMAGE_OR_FAMILY \
      --image-project $BASE_IMAGE_OR_FAMILY_PROJECT >& /dev/null&)

  echo "`date`: Uploading startup script to '$INSTANCE' when ready"
  gcloud compute copy-files \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --ssh-key-file $SSH_KEY_FILE \
      $install_script_path \
      $INSTANCE:.

  if [[ "$install_script_path" != "$INSTALL_SCRIPT" ]]; then
    rm $install_script_path
  fi

  args="--cloud_provider google --google_region us-central1 --quiet"
  args="$args --repository $DEBIAN_REPO_URL"
  if [[ "$ADD_CLOUD_LOGGING" == "true" ]]; then
      args="$args --google_cloud_logging"
  fi
  if [[ "$ADD_MONITORING" == "true" ]]; then
      args="$args --monitoring undef"
  fi

  command="sudo ./install-spinnaker-${TIME_DECORATOR}.sh ${args}"
  command="$command && sudo service spinnaker stop"
  if [[ "$UPDATE_OS" == "true" ]]; then
    command="$command && sudo DEBIAN_FRONTEND=noninteractive apt-get -y dist-upgrade"
    command="$command && sudo apt-get autoremove -y"
  fi

  echo "`date`: Installing Spinnaker onto '$INSTANCE'"
  gcloud compute ssh $INSTANCE \
    --project $PROJECT \
    --account $ACCOUNT \
    --zone $ZONE \
    --ssh-key-file $SSH_KEY_FILE \
    --command="$command"

  echo "`date`: Deleting '$INSTANCE' but keeping disk"
  gcloud compute instances set-disk-auto-delete $INSTANCE \
    --project $PROJECT \
    --account $ACCOUNT \
    --zone $ZONE \
    --no-auto-delete \
    --disk $INSTANCE

  # This will be on success too
  trap delete_prototype_disk EXIT

  # Just the builder instance, not the cleanup instance
  delete_build_instance
}


process_args "$@"
fix_defaults

create_empty_ssh_key
create_prototype_disk
clean_prototype_disk "$INSTANCE" "$INSTANCE_CLEANER"
image_from_prototype_disk "$TARGET_IMAGE" "$INSTANCE"

trap - EXIT

delete_prototype_disk

echo "`date`: DONE"

