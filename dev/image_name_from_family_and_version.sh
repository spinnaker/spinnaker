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

# This is useful for our build scripts/server to determine the desired name of 
# an image consistently based on the desired spinnaker version

if [ -z "$1" ] || [ -z "$2" ]; then
  >&2 echo "Both <family> and <version> must be supplied: "
  >&2 echo "    $ ./<executable> <family> <version>"
  exit 1
fi

FAMILY=$1
VERSION=$2

echo ${FAMILY}-${VERSION//./-}
