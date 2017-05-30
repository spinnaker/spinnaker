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

# Typical uses:
#
# Create an image from a debian repo.
#    build_google_image.sh \
#        --project $PROJECT \
#        --debian_repo https://dl.bintray.com/$BINTRAY_REPO \
#        --target_image $IMAGE
#
# Create tarball from an image
#    build_google_image.sh \
#        --project $PROJECT \
#        --source_image ${IMAGE}
#        --gz_uri gs://${BUCKET}/${IMAGE}.tar.gz


set -e
set -x

source $(dirname $0)/build_google_image_functions.sh

ADD_CLOUD_LOGGING=true
ADD_MONITORING=true
UPDATE_OS=true

TIME_DECORATOR=$(date +%Y%m%d%H%M%S)
DEBIAN_REPO_URL=https://dl.bintray.com/spinnaker/debians
INSTALL_SCRIPT=https://dl.bintray.com/spinnaker/scripts/InstallSpinnaker.sh

BASE_IMAGE_OR_FAMILY=ubuntu-1404-lts
IMAGE_PROJECT=
TARGET_IMAGE=

# If the source image is provided then do not install spinnaker since
# it is assumed the source image already has it.
# If a base image or family is provided, then use that to install spinnaker.
SOURCE_IMAGE=

DEFAULT_PROJECT=$(gcloud config list 2>&1 \
                  | grep "project =" | head -1 \
                  | sed "s/.* \(.*\)$/\1/")
DEFAULT_ACCOUNT=$(gcloud auth list 2>&1 \
                  | grep ACTIVE | head -1 \
                  | sed "s/.* \(.*\) ACTIVE/\1/")

# The build and prototype instance are aliases of one another.
# The BUILD_INSTANCE is the logical name
# The PROTOTYPE_INSTANCE is only set while the instance exists,
# for purposes of knowing whether or not to delete it.
BUILD_INSTANCE=
PROTOTYPE_INSTANCE=

# The cleaner instance is another instance we'll use to clean the
# disk without it being the boot disk.
# The pid is so we can start it in the background and wait on it
# later when we need it.
CLEANER_INSTANCE=
CLEANER_INSTANCE_PID=

ACCOUNT=$DEFAULT_ACCOUNT
PROJECT=$DEFAULT_PROJECT

ZONE=$(gcloud config list 2>&1 \
           | grep "zone =" | head -1 \
           | sed "s/.* \(.*\)$/\1/")

GZ_URI=""


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

   --source_image SOURCE_IMAGE
       [$SOURCE_IMAGE]
       Use SOURCE_IMAGE as the starting point. It already has spinnaker on it.

   --base_image BASE_IMAGE_OR_FAMILY
       [$BASE_IMAGE_OR_FAMILY]
       Use BASE_IMAGE_OR_FAMILY as the base image. Install spinnaker onto it.

   --image_project IMAGE_PROJECT
      [$IMAGE_PROJECT]
      The project for the SOURCE_IMAGE or BASE_IMAGE. The default is the
      PROJECT.

   --target_image TARGET_IMAGE
       [$TARGET_IMAGE]
       Produce the given TARGET_IMAGE. If empty, then do not produce an image.

   --gz_uri GZ_URI
       [none]
       Also extract the image to the specified a gs:// tar.gz URI.
       If empty then do not produce a disk_file.
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
            IMAGE_PROJECT=$1
            shift
            ;;
        --json_credentials)
            >&2 echo "--json_credentials is no longer used -- ignoring.  Use --account instead"
            shift
            ;;

        --source_image)
            SOURCE_IMAGE=$1
            shift
            ;;

        --zone)
            ZONE=$1
            shift
            ;;

        --gz_uri)
            GZ_URI=$1
            shift
            ;;
        *)
          show_usage
          >&2 echo "Unrecognized argument '$key'."
          exit -1
    esac
  done


  # Are we creating an image from an extracted URI or the disk itself.
  if [[ "$GZ_URI" != "" ]]; then
     if [[ "$GZ_URI" != gs://*.tar.gz ]]; then
       show_usage
       >&2 echo "$GZ_URI is not a gs:// tar.gz path."
       exit -1
     fi
  fi
}


function create_cleaner_instance() {
  # see https://cloud.google.com/compute/docs/instances/adding-removing-ssh-keys#instance-only
  local ssh_key=$(cat ${SSH_KEY_FILE}.pub)
  if [[ $ssh_key == ssh-rsa* ]]; then
      ssh_key="$LOGNAME:$ssh_key"
  fi
  gcloud compute instances create ${CLEANER_INSTANCE} \
      --metadata ssh-keys="$ssh_key" \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --machine-type n1-highmem-4 \
      --scopes storage-rw \
      --boot-disk-type pd-ssd \
      --boot-disk-size 20GB \
      --image-family ubuntu-1404-lts \
      --image-project ubuntu-os-cloud
}

function delete_cleaner_instance() {
  echo "Deleting cleaner instance '${CLEANER_INSTANCE}'"
  gcloud compute instances delete ${CLEANER_INSTANCE} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --quiet || true
  CLEANER_INSTANCE=

  if [[ "$BUILD_INSTANCE" != "" ]]; then
    echo "`date`: Deleting disk '$BUILD_INSTANCE'"
    gcloud compute disks delete $BUILD_INSTANCE \
        --project $PROJECT \
        --account $ACCOUNT \
        --zone $ZONE \
        --quiet || true
  fi
}


function create_prototype_disk() {
  local ssh_key=$(cat ${SSH_KEY_FILE}.pub)
  if [[ $ssh_key == ssh-rsa* ]]; then
      ssh_key="$LOGNAME:$ssh_key"
  fi

  if [[ "$SOURCE_IMAGE"  != "" ]]; then
    echo "`date` Creating disk '$BUILD_INSTANCE' from image '$SOURCE_IMAGE'"
    gcloud compute instances create ${BUILD_INSTANCE} \
        --no-boot-disk-auto-delete \
        --boot-disk-type pd-ssd \
        --boot-disk-size 10GB \
        --machine-type n1-standard-4 \
        --project $PROJECT \
        --account $ACCOUNT \
        --zone $ZONE \
        --image-project $IMAGE_PROJECT \
        --image $SOURCE_IMAGE \
        --quiet
    echo "`date` Almost there..."
    gcloud compute instances delete ${BUILD_INSTANCE} \
        --project $PROJECT \
        --account $ACCOUNT \
        --zone $ZONE \
        --quiet
    echo "`date` Finished creating disk '$BUILD_INSTANCE'"
    return 0
  fi

  echo "`date`: Fetching install script from $INSTALL_SCRIPT"
  local install_script_path

  if [[ -f "$INSTALL_SCRIPT" ]]; then
     install_script_path="$INSTALL_SCRIPT"
  else
    curl -sS $INSTALL_SCRIPT -o /tmp/install-spinnaker-${TIME_DECORATOR}.sh
    install_script_path=/tmp/install-spinnaker-${TIME_DECORATOR}.sh
    chmod +x $install_script_path
  fi

  echo "`date`: Creating prototype instance '$BUILD_INSTANCE'"
  gcloud compute instances create $BUILD_INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --machine-type n1-standard-2 \
      --boot-disk-type pd-ssd \
      --boot-disk-size 10GB \
      --image $BASE_IMAGE \
      --image-project $IMAGE_PROJECT \
      --metadata block-project-ssh-keys=TRUE

  # For purposes of cleaning up, remember this name.
  PROTOTYPE_INSTANCE=$BUILD_INSTANCE

  echo "`date` Adding ssh key to '$PROTOTYPE_INSTANCE'"
  gcloud compute instances add-metadata $PROTOTYPE_INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --metadata ssh-keys="$ssh_key"

  echo "`date`: Uploading startup script to '$PROTOTYPE_INSTANCE' when ready"
  local copied=false
  for i in {1..10}; do
      if gcloud compute copy-files \
          --project $PROJECT \
          --account $ACCOUNT \
          --zone $ZONE \
          --ssh-key-file $SSH_KEY_FILE \
          $install_script_path \
          $PROTOTYPE_INSTANCE:.; then
        copied=true
        break
     else
        sleep 1
        echo "trying again...."
     fi
  done

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

  command="sudo ./$(basename $install_script_path) ${args}"
  command="$command && sudo service spinnaker stop"

  echo "`date`: Installing Spinnaker onto '$PROTOTYPE_INSTANCE'"
  gcloud compute ssh $PROTOTYPE_INSTANCE \
    --project $PROJECT \
    --account $ACCOUNT \
    --zone $ZONE \
    --ssh-key-file $SSH_KEY_FILE \
    --command="$command"

  if [[ "$UPDATE_OS" == "true" ]]; then
    echo "`date`: Updating distribution on '$PROTOTYPE_INSTANCE'"
    gcloud compute ssh $PROTOTYPE_INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --ssh-key-file $SSH_KEY_FILE \
      --command="sudo DEBIAN_FRONTEND=noninteractive apt-get -y dist-upgrade && sudo apt-get autoremove -y"
  fi

  echo "`date`: Deleting '$PROTOTYPE_INSTANCE' but keeping disk"
  gcloud compute instances set-disk-auto-delete $PROTOTYPE_INSTANCE \
    --project $PROJECT \
    --account $ACCOUNT \
    --zone $ZONE \
    --no-auto-delete \
    --disk $PROTOTYPE_INSTANCE

  # Just the builder instance, not the cleanup instance
  delete_build_instance
}


function fix_defaults() {
  if [[ "$ZONE" == "" ]]; then
    ZONE=us-central1-f
  fi

  if [[ "$SOURCE_IMAGE" != "" ]]; then
    if [[ "$IMAGE_PROJECT" == "" ]]; then
       IMAGE_PROJECT=$PROJECT
    fi
  else
    # No source image, so assume a base image (to install from).
    local image_entry=$(gcloud compute images list 2>&1 \
                        | grep $BASE_IMAGE_OR_FAMILY | head -1)

    BASE_IMAGE=$(echo "$image_entry" | sed "s/\([^ ]*\) .*/\1/")

    # If this was a family, convert it to a particular image for
    # argument consistency
    if [[ "$IMAGE_PROJECT" == "" ]]; then
      IMAGE_PROJECT=$(echo "$image_entry" | sed "s/[^ ]* *\([^ ]*\)* .*/\1/")
    fi
  fi

  if [[ "$TARGET_IMAGE" != "" ]]; then
    BUILD_INSTANCE="build-${TARGET_IMAGE}-${TIME_DECORATOR}"
    CLEANER_INSTANCE="clean-${TARGET_IMAGE}-${TIME_DECORATOR}"
  elif [[ "$SOURCE_IMAGE" != "" ]]; then
    BUILD_INSTANCE="build-${SOURCE_IMAGE}-${TIME_DECORATOR}"
    CLEANER_INSTANCE="clean-${SOURCE_IMAGE}-${TIME_DECORATOR}"
  else
    >&2 echo "You must have either --source_image, or create a --target_image."
    exit -1
  fi
}


process_args "$@"
fix_defaults

ensure_empty_ssh_key

trap cleanup_instances_on_error EXIT
create_prototype_disk
SOURCE_DISK=$BUILD_INSTANCE

create_cleaner_instance
extract_clean_prototype_disk \
    "$SOURCE_DISK" "$CLEANER_INSTANCE" "$GZ_URI"

if [[ "$TARGET_IMAGE" != "" ]]; then
  image_from_prototype_disk "$TARGET_IMAGE" "$SOURCE_DISK"
fi

trap - EXIT

delete_cleaner_instance

echo "`date`: DONE"
