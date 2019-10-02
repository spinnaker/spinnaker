#!/usr/bin/env bash

set -e

docker build . -t halyard.compile -f Dockerfile.compile
docker build . -t halyard.local -f Dockerfile.local &
docker build . -t halyard.ubuntu -f Dockerfile.ubuntu &
wait
