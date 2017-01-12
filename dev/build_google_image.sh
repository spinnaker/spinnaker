#!/bin/bash
#
# Copyright 2015 Google Inc. All Rights Reserved.
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

CURRENT_IMAGE=$(gcloud compute images list 2>&1 | grep ubuntu-1404 | tail -1 | awk '{print $1}')

VARS="-var 'install_path=$(dirname $0)/../InstallSpinnaker.sh'"
VARS="$VARS -var 'source_image=$CURRENT_IMAGE'"
VARS="$VARS -var 'base_srcdir=$(dirname $0)/..'"

function process_args() {
  PROJECT_ID="ok"
  while [[ $# > 0 ]]
  do
      local key="$1"
      shift
      local value="$1"
      shift
      VARS="$VARS -var '${key#--}=$value'"
  done
}

process_args "$@"

eval "packer build $VARS $(dirname $0)/build_google_image.packer"
