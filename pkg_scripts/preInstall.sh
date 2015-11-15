#!/bin/sh

# check that owner group exists
if [ -z `getent group spinnaker` ]; then
  groupadd spinnaker
fi

# check that user exists
if [ -z `getent passwd spinnaker` ]; then
  useradd --gid spinnaker -m --home-dir /home/spinnaker spinnaker
fi

# Remove this after upstream services are fixed

if [ ! -d /home/spinnaker ]; then
  mkdir -p /home/spinnkaker/.aws
  chown -R spinnaker:spinnaker /home/spinnkaker
fi
