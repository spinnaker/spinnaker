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

# Import some functions from other scripts.
source $(dirname $0)/build_google_image_functions.sh


function show_usage() {
    fix_defaults

cat <<EOF
Usage:  $0 [options]

   --account ACCOUNT
       [$ACCOUNT]
       Use this gcloud account to build the image.

   --image_project IMAGE_PROJECT
      [$IMAGE_PROJECT]
      The project for the SOURCE_IMAGE or BASE_IMAGE. The default is the
      PROJECT.

   --install_script INSTALL_SCRIPT
       [$INSTALL_SCRIPT]
       The path or URL to the install script to use.

   --no_update_os
       Do not force an upgrade-dist of the base OS.

   --project PROJECT
       [$PROJECT]
       Publish (and build) the image in the PROJECT id.

   --version VERSION
       [$VERSION]
       The exact Spinnaker version we are baking images for.

   --zone ZONE
       [$ZONE]
       Zone to use when building the image. The final image is global.

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
        --account)
            ACCOUNT=$1
            shift
            ;;
        --image_project)
            IMAGE_PROJECT=$1
            shift
            ;;
        --install_script)
            INSTALL_SCRIPT=$1
            shift
            ;;
        --no_update_os)
            UPDATE_OS=false
            ;;
        --project)
            PROJECT=$1
            shift
            ;;
        --version)
            VERSION=$1
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


function create_component_prototype_disk() {
  echo "`date`: Fetching install script from $INSTALL_SCRIPT"
  local install_script_path
  local component=$1
  local version=$2

  if [[ -f "$INSTALL_SCRIPT" ]]; then
    install_script_path="$INSTALL_SCRIPT"
  else
    curl -sS $INSTALL_SCRIPT -o /tmp/install-spinnaker-${TIME_DECORATOR}.sh
    install_script_path=/tmp/install-spinnaker-${TIME_DECORATOR}.sh
    chmod +x $install_script_path
  fi

  echo "`date`: Creating prototype instance '$BUILD_INSTANCE'"
  # Assumes a firewall rule allowing tcp:22 for this tag.
  gcloud compute instances create $BUILD_INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --machine-type n1-standard-1 \
      --boot-disk-type pd-ssd \
      --tags allow-ssh \
      --image $BASE_IMAGE  \
      --image-project $IMAGE_PROJECT \
      --metadata block-project-ssh-keys=TRUE

  trap cleanup_instances_on_error EXIT

  PROTOTYPE_INSTANCE=$BUILD_INSTANCE
  echo "`date` Adding ssh key to '$BUILD_INSTANCE'"
  gcloud compute instances add-metadata $BUILD_INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --metadata-from-file ssh-keys=$HOME/.ssh/google_empty.pub

  # This second instance will be used later to clean the image
  # we dont need it yet, but will spin it up now to have it ready.
  # Assumes a firewall rule allowing tcp:22 for this tag.
  echo "`date` Warming up '$CLEANER_INSTANCE' for later"
  (gcloud compute instances create ${CLEANER_INSTANCE} \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --tags allow-ssh \
      --machine-type n1-standard-1 \
      --image $BASE_IMAGE \
      --image-project $IMAGE_PROJECT >& /dev/null&)

  echo "`date`: Uploading startup script to '$BUILD_INSTANCE' when ready"
  gcloud compute copy-files \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --ssh-key-file $SSH_KEY_FILE \
      $install_script_path \
      $BUILD_INSTANCE:.

  if [[ "$install_script_path" != "$INSTALL_SCRIPT" ]]; then
    rm $install_script_path
  fi

  args="--component $component --version $version"
  command="sudo ./install-spinnaker-${TIME_DECORATOR}.sh ${args}"
  if [[ -f $INSTALL_SCRIPT ]]; then
    command="sudo ./$(basename $INSTALL_SCRIPT) ${args}"
  fi

  echo "`date`: Installing $component and spinnaker-monitoring onto '$BUILD_INSTANCE'"
  gcloud compute ssh $BUILD_INSTANCE \
    --project $PROJECT \
    --account $ACCOUNT \
    --zone $ZONE \
    --ssh-key-file $SSH_KEY_FILE \
    --command="$command"

  if [[ "$UPDATE_OS" == "true" ]]; then
    echo "`date`: Updating distribution on '$BUILD_INSTANCE'"
    gcloud compute ssh $BUILD_INSTANCE \
      --project $PROJECT \
      --account $ACCOUNT \
      --zone $ZONE \
      --ssh-key-file $SSH_KEY_FILE \
      --command="sudo DEBIAN_FRONTEND=noninteractive apt-get -y dist-upgrade && sudo apt-get autoremove -y"
  fi

  echo "`date`: Deleting '$BUILD_INSTANCE' but keeping disk"
  gcloud compute instances set-disk-auto-delete $BUILD_INSTANCE \
    --project $PROJECT \
    --account $ACCOUNT \
    --zone $ZONE \
    --no-auto-delete \
    --disk $BUILD_INSTANCE

  # This will be on success too
  trap delete_prototype_disk EXIT

  # Just the builder instance, not the cleanup instance
  delete_build_instance
}


function create_component_image() {
  local comp=$1
  COMP_VERSION="$(hal version bom $VERSION --artifact-name ${comp} --quiet --color false)"
  # Target image is named spinnaker-${comp}-${comp-version} with dashes replacing dots.
  TARGET_IMAGE="$(echo spinnaker-${comp}-${COMP_VERSION} | sed 's/[\.:]/\-/g')"
  echo $TARGET_IMAGE
  CLEANER_INSTANCE="clean-${TARGET_IMAGE}"
  BUILD_INSTANCE="build-${TARGET_IMAGE}"

  create_component_prototype_disk $comp $VERSION
  extract_clean_prototype_disk "$BUILD_INSTANCE" "$CLEANER_INSTANCE"
  image_from_prototype_disk "$TARGET_IMAGE" "$BUILD_INSTANCE"

  trap - EXIT

  delete_prototype_disk
}


function fix_defaults() {
  # No source image, so assume a base image (to install from).
  if [[ "$SOURCE_IMAGE" == "" ]]; then
    local image_entry=$(gcloud compute images list 2>&1 \
                        | grep $BASE_IMAGE_OR_FAMILY | head -1)

    BASE_IMAGE=$(echo "$image_entry" | sed "s/\([^ ]*\) .*/\1/")

    # If this was a family, convert it to a particular image for
    # argument consistency
    if [[ "$IMAGE_PROJECT" == "" ]]; then
      IMAGE_PROJECT=$(echo "$image_entry" | sed "s/[^ ]* *\([^ ]*\)* .*/\1/")
    fi
  fi
}


process_args "$@"

declare -a COMPONENTS=('clouddriver' 'deck' 'echo' 'fiat' 'front50' 'gate' 'igor' 'orca' 'rosco' 'consul' 'vault' 'redis')
TIME_DECORATOR=$(date +%Y%m%d%H%M%S)
ZONE=us-central1-f
BASE_IMAGE_OR_FAMILY=ubuntu-1404-lts
SSH_KEY_FILE=$HOME/.ssh/google_empty

fix_defaults
create_empty_ssh_key

PIDS=
for comp in "${COMPONENTS[@]}"; do
  LOG="create-${comp}-image.log"
  echo "Creating component image for $comp, output will be logged to $LOG..."
  create_component_image $comp &> $LOG &
  PID=$!
  PIDS+=($PID)
done
echo "Waiting on PIDs: ${PIDS[@]}..."
wait ${PIDS[@]}

echo "`date`: DONE"
