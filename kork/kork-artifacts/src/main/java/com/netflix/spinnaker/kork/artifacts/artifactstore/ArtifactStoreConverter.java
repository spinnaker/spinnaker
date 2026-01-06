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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.exceptions.ArtifactStoreInvalidStateException;
import com.netflix.spinnaker.kork.artifacts.artifactstore.exceptions.ArtifactStoreInvalidTypeException;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Base64;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactStoreConverter {
  public static <T> T to(ObjectMapper mapper, Artifact artifact) {
    String t = artifact.getType();
    if (t == null) {
      throw new ArtifactStoreInvalidTypeException("null");
    }

    if (!t.endsWith("/" + ArtifactTypes.CommonAffixes.BASE64.asString())) {
      throw new ArtifactStoreInvalidTypeException("can only convert base64 artifact types " + t);
    }

    String ref = artifact.getReference();
    if (ref == null) {
      throw new ArtifactStoreInvalidStateException("cannot have null references");
    }

    byte[] b = Base64.getDecoder().decode(ref);
    try {
      return mapper.readValue(b, new TypeReference<>() {});
    } catch (Exception e) {
      throw new RuntimeException("could not convert to the appropriate type", e);
    }
  }
}
