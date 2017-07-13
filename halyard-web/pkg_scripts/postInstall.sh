#!/bin/sh

echo '#!/usr/bin/env bash' | sudo tee /usr/local/bin/hal > /dev/null
echo '/opt/halyard/bin/hal "$@"' | sudo tee /usr/local/bin/hal > /dev/null

chmod +x /usr/local/bin/hal

HAL_USER=""
if [ -f "/opt/spinnaker/config/halyard-user" ]; then
  HAL_USER=$(cat /opt/spinnaker/config/halyard-user)
fi

if [ -z "$HAL_USER" ];
  HAL_USER="ubuntu"
fi

install --mode=755 --owner=$HAL_USER --group=$HAL_USER --directory /var/log/spinnaker/halyard

service halyard restart
