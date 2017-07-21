#!/usr/bin/env bash

set -e

PLATFORMS=(debian docker)
VERSIONS=$@

./gradlew clean && ./gradlew installDist -Prelease.useLastTag=true

USAGE="You must supply a list of <versions>, e.g.\n $0 <version1> <version2>"

if [ -z "$VERSIONS" ]; then
  >&2 echo $USAGE
  exit 1
fi

for PLATFORM in "${PLATFORMS[@]}"; do
  echo "Building & releasing $PLATFORM..."
  ./release/$PLATFORM.sh
  for VERSION in $VERSIONS; do
    echo "Releasing $VERSION to $PLATFORM"
    ./release/publish.sh $VERSION $PLATFORM
  done
done
