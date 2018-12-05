#!/usr/bin/env bash

set -e
set -x

# Call this script from the root of the deck-functional-tests directory

BUILD_TAG="deck-functional-tests:local"
DOCKER_FILE=docker/Dockerfile.local
BUILD_CONTEXT=docker/context

docker build --rm -t "$BUILD_TAG" -f "$DOCKER_FILE" "$BUILD_CONTEXT"
