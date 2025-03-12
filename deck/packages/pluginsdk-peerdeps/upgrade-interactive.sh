#!/usr/bin/env bash

docker build -f Dockerfile.upgrade-interactive . -t spinnaker-deck-plugin-peerdeps-upgrade
docker run -v $PWD:/mnt/pluginsdk-peerdeps -it spinnaker-deck-plugin-peerdeps-upgrade:latest
