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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

/** A helper class that helps convert Artifact to and from some class. */
public class EntityHelper {
  private static final ObjectMapper mapper =
      new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final TypeReference<Map> mapType = new TypeReference<Map>() {};

  public static boolean isArtifactLike(Object v) {
    if (!(v instanceof Map)) {
      return false;
    }

    Map m = (Map) v;
    Object type = m.get("type");
    Object ref = m.get("reference");
    if (type == null || ref == null) {
      return false;
    }

    return type instanceof String && ref instanceof String;
  }

  public static boolean alreadyStored(Map<?, ?> m) {
    if (m.get("type") == null || m.get("reference") == null) {
      return false;
    }

    return ArtifactTypes.REMOTE_MAP_BASE64.getMimeType().equals(m.get("type"));
  }

  public static Artifact toArtifact(Map<?, ?> manifest, String artifactType) {
    try {
      String ref = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(manifest));
      return Artifact.builder().name("stored-entity").type(artifactType).reference(ref).build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static <K, V> Map<K, V> toMap(Artifact artifact) {
    return EntityHelper.to(artifact, EntityHelper.mapType);
  }

  public static <T> T to(Artifact artifact, TypeReference t) {
    String ref = artifact.getReference();
    if (ArtifactTypes.isRemote(artifact.getType())) {
      return (T) mapper.convertValue(artifact, t);
    }

    byte[] b = Base64.getDecoder().decode(ref);
    try {
      return (T) mapper.readValue(b, t);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
