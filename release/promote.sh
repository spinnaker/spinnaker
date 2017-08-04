#!/usr/bin/env bash

set -e

SOURCE_VERSION=$1
TARGET_VERSION=$2
PLATFORM=$3

USAGE="You must supply the <SOURCE_VERSION>, <TARGET_VERSION>, and <PLATFORM>:
  $0 <SOURCE_VERSION> <TARGET_VERSION> <PLATFORM>"

if [ -z "$SOURCE_VERSION" ] || [ -z "$TARGET_VERSION" ] || [ -z "$PLATFORM" ]; then
  >&2 echo "$USAGE"
  exit 1
fi

if [ "$PLATFORM" = "docker" ]; then
  SOURCE_IMAGE=gcr.io/spinnaker-marketplace/halyard:$SOURCE_VERSION
  TARGET_IMAGE=gcr.io/spinnaker-marketplace/halyard:$TARGET_VERSION

  docker pull $SOURCE_IMAGE
  docker tag $SOURCE_IMAGE $TARGET_IMAGE
  gcloud docker -- push $TARGET_IMAGE
else 
  SOURCE_PATH=gs://spinnaker-artifacts/halyard/$SOURCE_VERSION/$PLATFORM/halyard.tar.gz
  TARGET_PATH=gs://spinnaker-artifacts/halyard/$TARGET_VERSION/$PLATFORM/halyard.tar.gz

  gsutil cp $SOURCE_PATH halyard.tar.gz
  gsutil cp halyard.tar.gz $TARGET_PATH
  gsutil acl ch -u AllUsers:R $TARGET_PATH

  rm halyard.tar.gz
fi
