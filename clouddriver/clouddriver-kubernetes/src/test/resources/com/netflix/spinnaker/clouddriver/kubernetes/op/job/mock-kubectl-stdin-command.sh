#!/bin/sh
#
# Copyright 2022 Salesforce, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#

# This script is meant to mock kubectl apply -f - commands in unit tests. The functionality
# being tested is simply the fact that the retry attempts for such calls continue to read
# data from stdin. To simulate retries, we exit the script with an error that is configured
# to be retryable as long as $1 != "success"
input=$(cat -)
# simulate error case
if [ "$1" != "success" ]
then
  echo "\n########################" >&2
  echo "data received from stdin: $input" >&2
  echo "Error: TLS handshake timeout" >&2
  echo "########################" >&2
  exit 1
else
  echo "$input"
fi
