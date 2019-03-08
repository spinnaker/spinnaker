/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CloudFoundryServerGroupCreatorTest {
  @Test
  void generateCloudFoundryManifestFromDirectInput() throws IOException {
    String manifestPipelineJson = "{\n" +
      "  \"direct\": {\n" +
      "    \"buildpacks\": [\"java\"],\n" +
      "    \"diskQuota\": \"1024M\",\n" +
      "    \"environment\": [\n" +
      "      {\n" +
      "        \"key\": \"k\",\n" +
      "        \"value\": \"v\"\n" +
      "      }\n" +
      "    ],\n" +
      "    \"healthCheckHttpEndpoint\": \"http://healthme\",\n" +
      "    \"healthCheckType\": \"http\",\n" +
      "    \"instances\": 1,\n" +
      "    \"memory\": \"1024M\",\n" +
      "    \"routes\": [\"route\"],\n" +
      "    \"services\": [\"service\"]\n" +
      "  }\n" +
      "}";

    ObjectMapper mapper = new ObjectMapper();

    CloudFoundryServerGroupCreator.DirectManifest direct = mapper.readValue(manifestPipelineJson,
      CloudFoundryServerGroupCreator.Manifest.class).getDirect();

    assertThat(direct).isNotNull();
    assertThat(direct.toManifestYml()).isEqualTo(
      "---\n" +
        "applications:\n" +
        " -\n" +
        "  name: app\n" +
        "  buildpacks:\n" +
        "   - java\n" +
        "  health-check-type: http\n" +
        "  health-check-http-endpoint: http://healthme\n" +
        "  env:\n" +
        "    k: v\n" +
        "  routes:\n" +
        "   -\n" +
        "    route: route\n" +
        "  services:\n" +
        "   - service\n" +
        "  instances: 1\n" +
        "  memory: 1024M\n" +
        "  disk_quota: 1024M\n"
    );
  }
}