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
  IMAGE=halyard gcr.io/spinnaker-marketplace/halyard:$VERSION
  docker tag $IMAGE
  gcloud docker -- push $IMAGE
else 
  BUCKET_PATH=gs://spinnaker-artifacts/halyard/$VERSION/$PLATFORM/halyard.tar.gz

  gsutil cp halyard.tar.gz $BUCKET_PATH
  gsutil acl ch -u AllUsers:R $BUCKET_PATH
fi
