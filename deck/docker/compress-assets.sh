#!/usr/bin/env bash
# Copyright 2026 spinnaker.io
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

target_dir="${1:-.}"
if [[ ! -d "${target_dir}" ]]; then
  echo "Error: directory does not exist: ${target_dir}" >&2
  exit 1
fi

cd "${target_dir}"

# Keep the originals for clients without compression support. Runtime settings
# are replaced at startup, so pre-compressed copies would become stale.
find . -type f \( -name '*.js' -o -name '*.css' \) \
  ! -name 'settings*.js' -print0 | while IFS= read -r -d '' asset; do
  gzip -9 -n -c -- "${asset}" > "${asset}.gz"
  brotli -9 -f -k -- "${asset}"
done
