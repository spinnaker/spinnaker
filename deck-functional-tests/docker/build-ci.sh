#!/usr/bin/env bash

set -e
set -x

# Call this script from the root of the deck-functional-tests directory

BUILD_TAG="deck-functional-tests:ci"
PUBLISH_TAG="gcr.io/hebridean-sun/deck-functional-tests:ci"
DOCKER_FILE=docker/Dockerfile.ci
BUILD_CONTEXT=docker/context

docker build --rm -t "$BUILD_TAG" -f "$DOCKER_FILE" "$BUILD_CONTEXT"
docker tag "$BUILD_TAG" "$PUBLISH_TAG"
