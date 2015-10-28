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

REDIS_PORT=${REDIS_PORT:-6379}
REDIS_HOST=${REDIS_HOST:-127.0.0.1}

function is_local() {
  local ip="$1"
  if [[ "$ip" == "localhost" || "$ip" == "$(hostname)" || "$ip" == "0.0.0.0" ]]; then
      return 0
  elif ifconfig | grep " inet addr:${ip} "  > /dev/null; then
      return 0
  else
      return 1
  fi
}

function maybe_start_redis() {
  if is_local "$REDIS_HOST"; then
    echo "Starting Redis on $REDIS_HOST"
    sudo service redis-server start
  else
    echo "Using remote Redis from $REDIS_HOST:$REDIS_PORT"
  fi
}


echo "Checking for Redis on $REDIS_HOST:$REDIS_PORT."
if nc -z $REDIS_HOST $REDIS_PORT; then
  echo "Redis is already up on $REDIS_HOST:$REDIS_PORT."
else
  maybe_start_redis
  echo "Waiting for Redis to start accepting requests on" \
       "$REDIS_HOST:$REDIS_PORT..."
  while ! nc -z $REDIS_HOST $REDIS_PORT; do sleep 0.1; done
  echo "Redis is up."
fi
