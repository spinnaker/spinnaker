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
package com.netflix.spinnaker.kork.artifacts.artifactstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.exceptions.ArtifactStoreInvalidTypeException;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArtifactStoreConverterTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void checkToMap() {
    Map<String, Object> m = Map.of("foo", "bar", "array", List.of(1, 2, 3));

    byte[] b = null;
    try {
      b = mapper.writeValueAsBytes(m);
    } catch (JsonProcessingException e) {
      fail("JSON serialization exception", e);
    }
    String b64ref = Base64.getEncoder().encodeToString(b);
    final Artifact artifact = Artifact.builder().type("foo").reference(b64ref).build();

    assertThrows(
        ArtifactStoreInvalidTypeException.class, () -> ArtifactStoreConverter.to(mapper, artifact));
    Artifact valid =
        Artifact.builder()
            .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
            .reference(b64ref)
            .build();
    try {
      Map<String, Object> result = ArtifactStoreConverter.to(mapper, valid);
      assertEquals(m, result);
    } catch (Exception e) {
      fail("unexpected exception", e);
    }
  }
}
