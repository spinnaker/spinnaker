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

SOURCE_DIR=$(dirname $0)
HAVE_KEYS=0

function prompt_if_unset() {
  local name=$1
  local tmp
  while [[ "${!name}" == "" ]]; do
      read -e -p "ENTER $name: " tmp
      eval ${name}=$tmp
  done
}

prompt_if_unset DATADOG_API_KEY
prompt_if_unset DATADOG_APP_KEY

environ_file=$(readlink -f "${SOURCE_DIR}/../../environ")
echo "Storing keys into $environ_file"
if [[ ! -f "${environ_file}" ]]; then
  sudo touch "${environ_file}"
fi
sudo chmod 600 "${environ_file}"
sudo cat >> "$environ_file" <<EOF
DATADOG_API_KEY=$DATADOG_API_KEY
DATADOG_APP_KEY=$DATADOG_APP_KEY
EOF


echo "Installing Datadog Agent"
DD_API_KEY=$DATADOG_API_KEY bash -c "$(curl -L https://raw.githubusercontent.com/DataDog/dd-agent/master/packaging/datadog-agent/source/install_agent.sh)"

for dashboard in ${SOURCE_DIR}/*Timeboard.json; do
  echo "Installing $(basename $dashboard)"
  curl -X POST -H "Content-type: application/json" \
       -d "@${dasboard}"
      "https://app.datadoghq.com/api/v1/dash?api_key=${DATADOG_API_KEY}&application_key=${DATADOG_APP_KEY}"
done

