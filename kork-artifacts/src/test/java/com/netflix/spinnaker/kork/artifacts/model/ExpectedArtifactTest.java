/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.kork.artifacts.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class ExpectedArtifactTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void deserialize() throws IOException {
    ExpectedArtifact originalArtifact =
        ExpectedArtifact.builder()
            .id("test")
            .usePriorArtifact(true)
            .useDefaultArtifact(false)
            .matchArtifact(Artifact.builder().type("gcs/object").name("my-artifact").build())
            .build();

    String json = objectMapper.writeValueAsString(originalArtifact);
    ExpectedArtifact deserializedArtifact = objectMapper.readValue(json, ExpectedArtifact.class);
    assertThat(originalArtifact).isEqualTo(deserializedArtifact);
  }
}
