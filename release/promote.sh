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
  PUBLISH_HALYARD_DOCKER_IMAGE_BASE=${PUBLISH_HALYARD_DOCKER_IMAGE_BASE:-gcr.io/spinnaker-marketplace/halyard}
  SOURCE_IMAGE=$PUBLISH_HALYARD_DOCKER_IMAGE_BASE:$SOURCE_VERSION
  TARGET_IMAGE=$PUBLISH_HALYARD_DOCKER_IMAGE_BASE:$TARGET_VERSION

  docker pull $SOURCE_IMAGE
  docker tag $SOURCE_IMAGE $TARGET_IMAGE
  echo "Pushing docker image $TARGET_NAME"
  gcloud docker -- push $TARGET_IMAGE
else 
  PUBLISH_HALYARD_BUCKET_BASE_URL=${PUBLISH_HALYARD_BUCKET_BASE_URL:-gs://spinnaker-artifacts/halyard}
  SOURCE_URL=$PUBLISH_HALYARD_BUCKET_BASE_URL/$SOURCE_VERSION/$PLATFORM/halyard.tar.gz
  TARGET_URL=$PUBLISH_HALYARD_BUCKET_BASE_URL/$TARGET_VERSION/$PLATFORM/halyard.tar.gz

  echo "Pushing halyard.tar.gz to $TARGET_URL"
  gsutil cp $SOURCE_URL halyard.tar.gz
  gsutil cp halyard.tar.gz $TARGET_URL
  gsutil acl ch -u AllUsers:R $TARGET_URL

  rm halyard.tar.gz
fi
