#!/bin/bash
# Copyright 2016 Google Inc. All Rights Reserved.
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

# This script is specific to preparing a Google-hosted virtual machine
# for running Spinnaker when the instance was created
# from the public source to prod codelab image.

if [[ `/usr/bin/id -u` -ne 0 ]];then
  echo "$0 must be executed with root permissions; exiting"
  exit 1
fi

/opt/spinnaker/install/first_google_boot.sh

echo "Setting hostname for rosco deb repo"
sed -i "s/debianRepository:.*/debianRepository: http:\/\/$(hostname):9999\/ trusty main/" /opt/rosco/config/rosco.yml

/opt/spinnaker/bin/reconfigure_spinnaker.sh
service spinnaker restart
