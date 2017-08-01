#!/usr/bin/env bash

set -e

docker build . -t halyard -f Dockerfile.local
