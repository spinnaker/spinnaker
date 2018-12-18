#!/bin/bash

# Copyright 2017 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

HAL="hal -q --log=info "

echo "Waiting for halyard to start running..."

set +e
$HAL --ready &> /dev/null

while [ "$?" != "0" ]; do
  $HAL --ready &> /dev/null
done
set -e

hal config ci jenkins enable
echo admin | hal config ci jenkins master add CodelabJenkins --address http://localhost:5656 --username admin --password

sudo -u ubuntu echo "debianRepository: http://$(hostname):9999/ trusty main" \
    > ~ubuntu/.hal/default/profiles/rosco-local.yml

sudo -u ubuntu cat <<EOF > ~ubuntu/.hal/default/profiles/clouddriver-local.yml
credentials:
  challengeDestructiveActionsEnvironments:
EOF

/var/spinnaker/startup/first_halyard_boot.sh
