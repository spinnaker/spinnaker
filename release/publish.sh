#!/usr/bin/env bash

set -e

VERSION=$1
PLATFORM=$2

USAGE="You must supply <version> and <platform>, e.g.\n $0 <version> <platform>"

if [ -z "$VERSION" ]; then
  >&2 echo $USAGE
  exit 1
fi

if [ -z "$PLATFORM" ]; then
  >&2 echo $USAGE
  exit 1
fi

./release/$PLATFORM.sh

if [ "$PLATFORM" = "docker" ]; then
  PUBLISH_HALYARD_DOCKER_IMAGE_BASE=${PUBLISH_HALYARD_DOCKER_IMAGE_BASE:-gcr.io/spinnaker-marketplace/halyard}
  IMAGE=$PUBLISH_HALYARD_DOCKER_IMAGE_BASE:$VERSION

  echo "Pushing docker image $IMAGE"
  docker tag halyard $IMAGE
  gcloud docker -- push $IMAGE
else 
  PUBLISH_HALYARD_BUCKET_BASE_URL=${PUBLISH_HALYARD_BUCKET_BASE_URL:-gs://spinnaker-artifacts/halyard}
  BUCKET_URL=$PUBLISH_HALYARD_BUCKET_BASE_URL/$VERSION/$PLATFORM/halyard.tar.gz

  echo "Pushing halyard.tar.gz to $BUCKET_URL"
  gsutil cp halyard.tar.gz $BUCKET_URL
  gsutil acl ch -u AllUsers:R $BUCKET_URL
fi
