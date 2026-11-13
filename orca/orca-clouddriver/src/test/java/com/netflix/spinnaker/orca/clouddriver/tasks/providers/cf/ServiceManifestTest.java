/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ServiceManifestTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void deserializeParametersAndCredentialsAsMaps() throws Exception {
    String json =
        "{\n"
            + "  \"direct\": {\n"
            + "    \"service\": \"my-service\",\n"
            + "    \"parameters\": {\"key\": \"value\"},\n"
            + "    \"credentials\": {\"user\": \"secret\"}\n"
            + "  }\n"
            + "}";

    ServiceManifest manifest = mapper.readValue(json, ServiceManifest.class);

    assertThat(manifest.getDirect()).isNotNull();
    assertThat(manifest.getDirect().getParameters()).containsEntry("key", "value");
    assertThat(manifest.getDirect().getCredentials()).containsEntry("user", "secret");
  }

  @Test
  void deserializeParametersAndCredentialsAsStringifiedJson() throws Exception {
    String json =
        "{\n"
            + "  \"direct\": {\n"
            + "    \"service\": \"my-service\",\n"
            + "    \"parameters\": \"{\\\"key\\\": \\\"value\\\"}\",\n"
            + "    \"credentials\": \"{\\\"user\\\": \\\"secret\\\"}\"\n"
            + "  }\n"
            + "}";

    ServiceManifest manifest = mapper.readValue(json, ServiceManifest.class);

    assertThat(manifest.getDirect()).isNotNull();
    assertThat(manifest.getDirect().getParameters()).containsEntry("key", "value");
    assertThat(manifest.getDirect().getCredentials()).containsEntry("user", "secret");
  }

  @Test
  void deserializeParametersAsStringifiedYaml() throws Exception {
    String json =
        "{\n"
            + "  \"direct\": {\n"
            + "    \"service\": \"my-service\",\n"
            + "    \"parameters\": \"key: value\\nother: 123\"\n"
            + "  }\n"
            + "}";

    ServiceManifest manifest = mapper.readValue(json, ServiceManifest.class);

    assertThat(manifest.getDirect()).isNotNull();
    assertThat(manifest.getDirect().getParameters())
        .containsEntry("key", "value")
        .containsEntry("other", 123);
  }
}
