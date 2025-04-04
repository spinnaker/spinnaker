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
cd .. # Swap to monorepo root
# Gradle 7.6 --rerun omits the previous need for a mid-build :gate:clean
./gradlew :gate:gate-web:test --rerun --tests '*GenerateSwagger*'
cd 'gate' || exit 1 # Back to Gate

# springdoc 1.8.x (the version compatible with Spring 2.x) does not generate schema when @RequestBody(required = false)
# So, this exists to manually patch all of those places with the proper schema
RB_OPT_SCHEMA='{"content":{"application/json":{"schema":{"additionalProperties":{"type":"object"},"type":"object"}}},"required":false}'

# It would be really nice if jq did in-place editing
cat ./gate-web/swagger.json | jq --argjson rbschema "$RB_OPT_SCHEMA" '.paths.["/applications/{application}/pipelineConfigs/{pipelineName}"].post.requestBody = $rbschema' > ./gate-web/swagger2.json
mv ./gate-web/swagger2.json ./gate-web/swagger.json
cat ./gate-web/swagger.json | jq --argjson rbschema "$RB_OPT_SCHEMA" '.paths.["/pipelines/v2/{application}/{pipelineNameOrId}"].post.requestBody = $rbschema' > ./gate-web/swagger2.json
mv ./gate-web/swagger2.json ./gate-web/swagger.json
cat ./gate-web/swagger.json | jq --argjson rbschema "$RB_OPT_SCHEMA" '.paths.["/pipelines/{application}/{pipelineNameOrId}"].post.requestBody = $rbschema' > ./gate-web/swagger2.json
mv ./gate-web/swagger2.json ./gate-web/swagger.json

touch swagger/swagger.json
cat gate-web/swagger.json | json_pp > swagger/swagger.json
rm gate-web/swagger.json
