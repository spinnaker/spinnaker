#!/usr/bin/env bash

set -e

SOURCE_VERSION=$1
TARGET_VERSION=$2
PLATFORMS=(debian docker macos)

USAGE="You must supply the <SOURCE_VERSION>, and <TARGET_VERSION>:
  $0 <SOURCE_VERSION> <TARGET_VERSION>"

if [ -z "$SOURCE_VERSION" ] || [ -z "$TARGET_VERSION" ]; then
  >&2 echo "$USAGE"
  exit 1
fi

for PLATFORM in "${PLATFORMS[@]}"; do
  echo "Promoting $PLATFORM from $SOURCE_VERSION to $TARGET_VERSION..."
  ./release/promote.sh $SOURCE_VERSION $TARGET_VERSION $PLATFORM
done
