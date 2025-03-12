#!/bin/bash
# Copyright 2018 Google Inc. All Rights Reserved.
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

## NOTE: This script is expected to be run from the root 'gate' directory

rm -f swagger/swagger.json
# Runs GenerateSwagger.groovy Spock Spec to generate the swagger spec.
# This Spec enables optional but standard features to document the full API.
./gradlew clean && ./gradlew gate-web:test --tests *GenerateSwagger*
touch swagger/swagger.json
cat gate-web/swagger.json | json_pp > swagger/swagger.json
rm gate-web/swagger.json
