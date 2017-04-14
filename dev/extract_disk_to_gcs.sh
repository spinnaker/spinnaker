#!/bin/bash
#
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

set -e
set -x

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <disk-id to extract> <gcs path to write to>"
  exit -1
fi

disk_id="$1"
gcs_path="$2"
if [[ "$gcs_path" != gs://*.tar.gz ]]; then
  echo "'$gcs_path' is not a Google Cloud Storage URI to a tar.gz (gs://*)"
  exit -1
fi

# https://cloud.google.com/compute/docs/images/import-existing-image#plan_import
echo "`date` Dumping disk $disk_id"
sudo dd if=/dev/disk/by-id/google-${disk_id} \
        of=./disk.raw \
        bs=4M \
        conv=sparse

echo "`date` Creating tar file"
sudo tar -Sczf disk.raw.tar.gz disk.raw

echo "`date` Writing $gcs_path"
gsutil -q cp disk.raw.tar.gz ${gcs_path}

echo "`date` Finished"



