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

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <disk-id to clean>"
  exit -1
fi

disk_id=$1


# Mount the dirty disk
mkdir -p /mnt/disks/${disk_id}
mount -t ext4 -o discard,defaults \
      /dev/disk/by-id/google-${disk_id}-part1 \
      /mnt/disks/${disk_id}
cd /mnt/disks/${disk_id}
echo "FYI, disk Usage is `df -kh .`"


# Remove user information from etc/ and their home directories.
# But leave spinnaker.
etc_files="group gshadow passwd shadow subgid subuid"
for user in $(ls home); do
  if [[ -f home/${user}/keep_user ]]; then
    # If we want to keep non-standard users, then
    # mark them with a keep_user in their root directory.
    # We'll remove the marker here, but keep the user intact.
    rm -f home/${user}/keep_user
    continue
  fi

  if [[ "$user" != "spinnaker" && "$user" != "ubuntu" ]]; then
    for file in $etc_files; do
      sed /^$user:.*/d -i etc/${file} || true
      sed /^$user:.*/d -i etc/${file}- || true
    done
    rm -rf home/$user;
  fi
done


# Remove authorized keys
if [[ -f root/.ssh/authorized_keys ]]; then
  cat /dev/null > root/.ssh/authorized_keys
fi

# Remove tmp and log files
rm -rf tmp/*
find var/log -type f -exec rm {} \;

cd /
umount /mnt/disks/${disk_id}
