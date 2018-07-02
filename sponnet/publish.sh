#!/usr/bin/env bash

set -e
set -x

if [ -z "$VERSION" ]; then
  echo -e "No version to release specified with the \$VERSION env var, exiting"
  exit 1
fi

if [ -z "$GCS_BUCKET_PATH" ]; then
  echo "No GCS bucket specified using \$GCS_BUCKET_PATH, using gs://spinnaker-artifacts/sponnet"
  GCS_BUCKET_PATH="gs://spinnaker-artifacts/sponnet"
fi

tar -czvf sponnet.tar.gz *.libsonnet

gsutil cp sponnet.tar.gz $GCS_BUCKET_PATH/$VERSION/

echo $VERSION > latest

gsutil cp latest $GCS_BUCKET_PATH/

rm latest
rm sponnet.tar.gz
