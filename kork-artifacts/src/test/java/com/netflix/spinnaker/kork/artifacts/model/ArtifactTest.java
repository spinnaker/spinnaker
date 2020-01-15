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
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class ArtifactTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void deserialize() throws IOException {
    Artifact originalArtifact =
        Artifact.builder()
            .type("gcs/object")
            .customKind(false)
            .uuid("6b9a5d0b-5706-41da-b379-234c27971482")
            .name("my-artifact")
            .version("3")
            .metadata(ImmutableMap.<String, Object>builder().put("test", "123").build())
            .location("somewhere")
            .provenance("history")
            .build();

    String json = objectMapper.writeValueAsString(originalArtifact);
    Artifact deserializedArtifact = objectMapper.readValue(json, Artifact.class);
    assertThat(originalArtifact).isEqualTo(deserializedArtifact);
  }

  @Test
  public void unknownKeysInMetadata() {
    ImmutableMap<String, String> originalArtifact =
        ImmutableMap.<String, String>builder().put("id", "123").put("name", "my-artifact").build();

    Artifact deserializedArtifact = objectMapper.convertValue(originalArtifact, Artifact.class);
    assertThat(deserializedArtifact.getMetadata()).containsEntry("id", "123");
  }

  @Test
  public void kindIsIgnored() {
    ImmutableMap<String, String> originalArtifact =
        ImmutableMap.<String, String>builder()
            .put("kind", "test")
            .put("name", "my-artifact")
            .build();

    Artifact deserializedArtifact = objectMapper.convertValue(originalArtifact, Artifact.class);
    Map<String, Object> metadata =
        Optional.ofNullable(deserializedArtifact.getMetadata()).orElseGet(HashMap::new);
    assertThat(metadata).doesNotContainKey("kind");
  }
}
