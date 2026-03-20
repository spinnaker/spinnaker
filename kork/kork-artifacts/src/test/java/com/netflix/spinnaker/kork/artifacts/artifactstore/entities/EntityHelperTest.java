/*
 * Copyright 2025 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.entities;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.junit.jupiter.api.Test;

class EntityHelperTest {
  @Data
  public static class Mock {
    public String foo;
    public Integer bar;
    public Mock nested;

    public Mock() {}

    public Mock(String foo, Integer bar, Mock nested) {
      this.foo = foo;
      this.bar = bar;
      this.nested = nested;
    }
  }

  @Test
  void toArtifact() throws IOException {
    Map<String, Object> obj = Map.of("foo", "bar", "array", List.of(1, 2, 3));

    Artifact artifact =
        EntityHelper.toArtifact(obj, ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType());
    assertEquals(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType(), artifact.getType());
    ObjectMapper mapper = new ObjectMapper();
    byte[] reference = Base64.getDecoder().decode(artifact.getReference());
    Map convert = mapper.readValue(reference, Map.class);
    assertEquals(obj, convert);
  }

  @Test
  void toMap() {
    Map<String, Object> obj = Map.of("foo", "bar", "array", List.of(1, 2, 3));

    Artifact artifact =
        EntityHelper.toArtifact(obj, ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType());
    Map converted = EntityHelper.toMap(artifact);
    assertEquals(obj, converted);
  }

  @Test
  void to() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    Mock mock = new Mock("string", 1, new Mock("nested-string", 2, null));
    String b64Str = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(mock));
    Artifact artifact =
        Artifact.builder()
            .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
            .reference(b64Str)
            .build();

    Mock converted = EntityHelper.to(artifact, new TypeReference<Mock>() {});
    assertEquals(mock, converted);
  }
}
