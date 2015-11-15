#!/bin/sh

# check that owner group exists
if [ -z `getent group spinnaker` ]; then
  groupadd spinnaker
fi

# check that user exists
if [ -z `getent passwd spinnaker` ]; then
  useradd --gid spinnaker -m --home-dir /home/spinnaker spinnaker
fi
