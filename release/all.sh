#!/usr/bin/env bash

set -e

./gradlew clean && ./gradlew installDist

PLATFORMS=(debian)
VERSION=$1

USAGE="You must supply <version>, e.g.\n $0 <version>"

if [ -z "$VERSION" ]; then
  >&2 echo $USAGE
  exit 1
fi

for PLATFORM in "${PLATFORMS[@]}"; do
  echo "Building & releasing $PLATFORM..."
  ./release/$PLATFORM.sh
  ./release/publish.sh $VERSION $PLATFORM
done
