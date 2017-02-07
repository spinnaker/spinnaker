#!/bin/sh

# ubuntu
# check that owner group exists
if [ -z `getent group spinnaker` ]; then
  groupadd spinnaker
fi

# check that user exists
if [ -z `getent passwd spinnaker` ]; then
  useradd --gid spinnaker spinnaker -m --home-dir /home/spinnaker
fi


echo '#!/usr/bin/env bash' | sudo tee /usr/local/bin/hal
echo '/opt/halyard/bin/hal "$@"' | sudo tee /usr/local/bin/hal

chmod +x /usr/local/bin/hal

install --mode=755 --owner=spinnaker --group=spinnaker --directory  /var/log/spinnaker/halyard 
