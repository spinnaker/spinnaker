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
#        --project_id $PROJECT \
#        --debian_repo https://dl.bintray.com/$BINTRAY_REPO \
#        --target_image $IMAGE
#
# Create an image from a debian repo, but keep the disk around.
#    build_google_image.sh \
#        --project_id $PROJECT \
#        --debian_repo https://dl.bintray.com/$BINTRAY_REPO \
#        --target_image $IMAGE \
#        --target_disk ${IMAGE}-disk
#
# Create tarball from a previous run's disk.
# We could have created this will the image, but it takes a long
# time and we might want to test the image first.
#    build_google_image.sh \
#        --project_id $PROJECT \
#        --source_disk ${IMAGE}-disk
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
BASE_IMAGE_OR_FAMILY_PROJECT=
TARGET_IMAGE=

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

SOURCE_DISK=""
TARGET_DISK=""
GZ_URI=""


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

  if [[ "$SOURCE_DISK" != "" ]]; then
    BUILD_INSTANCE=""  # dont create an instance
    CLEANER_INSTANCE="clean-${SOURCE_DISK}-${TIME_DECORATOR}"
  elif [[ "$TARGET_IMAGE" != "" ]]; then
    if [[ "$BUILD_INSTANCE" == "" ]]; then
      BUILD_INSTANCE="build-${TARGET_IMAGE}-${TIME_DECORATOR}"
    fi
    CLEANER_INSTANCE="clean-${TARGET_IMAGE}-${TIME_DECORATOR}"
  else
    >&2 echo "If you do not have a --source_disk then you must create an image."
    exit -1
  fi
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

   --source_disk SOURCE_DISK
       [$SOURCE_DISK]
       If not empty, then create the image from this disk.

   --target_disk TARGET_DISK
       [$TARGET_DISK]
       If not empty "" then also keep the disk used to create the image.
       Otherwise, delete the disk when done. If keeping the disk, then give
       it TARGET_DISK.

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
            >&2 echo "--image_project is no longer used -- ignoring."
            shift
            ;;
        --json_credentials)
            >&2 echo "--json_credentials is no longer used -- ignoring.  Use --account instead"
            shift
            ;;

        --source_disk)
            SOURCE_DISK="$1"
            shift
            ;;

        --target_disk)
            TARGET_DISK="$1"
            BUILD_INSTANCE="$1"
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


function create_cleaner_instance() {
  # This instance will be used later to clean the image
  # we dont need it yet, but will spin it up now to have it ready.
  # this has a bigger disk so we can store a copy of the original disk on it.
  # Give this a lot of ram because we're going to tar up and compress the
  # disk.
  echo "`date` Warming up '$CLEANER_INSTANCE' for later"
  gcloud compute instances create ${CLEANER_INSTANCE} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --machine-type n1-highmem-4 \
      --scopes storage-rw \
      --boot-disk-type pd-ssd \
      --boot-disk-size 20GB \
      --image $BASE_IMAGE_OR_FAMILY \
      --image-project $BASE_IMAGE_OR_FAMILY_PROJECT >& /dev/null&
  CLEANER_INSTANCE_PID=$!

  trap cleanup_instances_on_error EXIT
}

function delete_cleaner_instance() {
  echo "Deleting cleaner instance '${CLEANER_INSTANCE}'"
  gcloud compute instances delete ${CLEANER_INSTANCE} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --quiet || true

  if [[ "$TARGET_DISK" != "" ]]; then
    echo "`date`: Keeping disk '$TARGET_DISK'"
  elif [[ "$BUILD_INSTANCE" != "" ]]; then
    echo "`date`: Deleting disk '$BUILD_INSTANCE'"
    gcloud compute disks delete $BUILD_INSTANCE \
        --project $PROJECT \
        --account $ACCOUNT \
        --zone $ZONE \
        --quiet || true
  fi
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

  echo "`date`: Creating prototype instance '$BUILD_INSTANCE'"
  gcloud compute instances create $BUILD_INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --machine-type n1-standard-1 \
      --boot-disk-type pd-ssd \
      --boot-disk-size 10GB \
      --image $BASE_IMAGE_OR_FAMILY \
      --image-project $BASE_IMAGE_OR_FAMILY_PROJECT \
      --metadata block-project-ssh-keys=TRUE

  # For purposes of cleaning up, remember this name.
  PROTOTYPE_INSTANCE=$BUILD_INSTANCE

  echo "`date` Adding ssh key to '$PROTOTYPE_INSTANCE'"
  gcloud compute instances add-metadata $PROTOTYPE_INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --metadata-from-file ssh-keys=$HOME/.ssh/google_empty.pub

  echo "`date`: Uploading startup script to '$PROTOTYPE_INSTANCE' when ready"
  gcloud compute copy-files \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --ssh-key-file $SSH_KEY_FILE \
      $install_script_path \
      $PROTOTYPE_INSTANCE:.

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

  # This will be on success too
  trap delete_cleaner_instance EXIT

  # Just the builder instance, not the cleanup instance
  delete_build_instance
}


process_args "$@"
fix_defaults

create_empty_ssh_key
create_cleaner_instance
if [[ "$SOURCE_DISK" == "" ]]; then
  create_prototype_disk
  SOURCE_DISK=$BUILD_INSTANCE
fi

if [[ "$GZ_URI" != "" ]]; then
  IMAGE_SOURCE=$GZ_URI
else
  IMAGE_SOURCE=$SOURCE_DISK
fi

echo "Waiting on ${CLEANER_INSTANCE}...."
wait $CLEANER_INSTANCE_PID || true
extract_clean_prototype_disk \
    "$SOURCE_DISK" "$CLEANER_INSTANCE" "$GZ_URI"

image_from_prototype_disk "$TARGET_IMAGE" "$IMAGE_SOURCE"

trap - EXIT

delete_cleaner_instance

echo "`date`: DONE"
