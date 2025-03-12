/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NexusArtifactExtractorTest {
  @Test
  void extractNexusArtifact() throws IOException {
    ObjectMapper mapper = EchoObjectMapper.getInstance();

    String payloadStr =
        "{"
            + "\"action\": \"UPDATED\","
            + "\"component\": {"
            + "   \"format\": \"maven2\","
            + "   \"group\": \"io.pivotal.spinnaker\","
            + "   \"id\": \"76d7d7e4186a390fd96db295b80986ab\","
            + "   \"name\": \"multifoundationmetrics\","
            + "   \"version\": \"0.3.3\""
            + "},"
            + "\"initiator\": \"admin/172.17.0.1\","
            + "\"nodeId\": \"25F88840-1F7BAEDC-C7C8DFE0-DF41CE6C-697E9B6C\","
            + "\"repositoryName\": \"maven-releases\","
            + "\"timestamp\": \"2019-05-10T16:08:38.565+0000\""
            + "}";

    Map payload = mapper.readValue(payloadStr, Map.class);

    NexusArtifactExtractor extractor = new NexusArtifactExtractor();

    assertThat(extractor.getArtifacts("nexus", payload))
        .containsExactly(
            Artifact.builder()
                .type("maven/file")
                .name("io.pivotal.spinnaker:multifoundationmetrics")
                .reference("io.pivotal.spinnaker:multifoundationmetrics:0.3.3")
                .version("0.3.3")
                .provenance("maven-releases")
                .build());
  }
}
