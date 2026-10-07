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
import java.util.HashMap;
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
      Artifact.ArtifactBuilder builder =
          Artifact.builder().name("stored-entity").type(artifactType).reference(ref);
      applyManifestIdentity(builder, manifest);
      return builder.build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Best-effort: if {@code manifest} looks like a Kubernetes/CloudRun manifest (has a "kind" and
   * "metadata.name"), surface those on the stored placeholder's name/metadata. Without this, the
   * placeholder that replaces the manifest in the execution context carries nothing but an opaque
   * reference, so callers like deck's DeployStatus can't show what was stored without fetching the
   * full content.
   */
  private static void applyManifestIdentity(Artifact.ArtifactBuilder builder, Map<?, ?> manifest) {
    Object kindObj = manifest.get("kind");
    Object metadataObj = manifest.get("metadata");
    if (!(kindObj instanceof String) || !(metadataObj instanceof Map)) {
      return;
    }

    Object nameObj = ((Map<?, ?>) metadataObj).get("name");
    if (!(nameObj instanceof String)) {
      return;
    }

    String kind = (String) kindObj;
    String name = (String) nameObj;
    Object namespaceObj = ((Map<?, ?>) metadataObj).get("namespace");
    String namespace = namespaceObj instanceof String ? (String) namespaceObj : null;

    Map<String, Object> identity = new HashMap<>();
    identity.put("kind", kind);
    identity.put("name", name);
    if (namespace != null) {
      identity.put("namespace", namespace);
    }

    builder
        .name(namespace != null ? kind + " " + namespace + "/" + name : kind + " " + name)
        .metadata(identity);
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
