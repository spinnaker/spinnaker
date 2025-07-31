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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypeDecorator;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.filters.ApplicationFilter;
import com.netflix.spinnaker.kork.artifacts.artifactstore.filters.ApplicationStorageFilter;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ManifestStorageHandler is used to convert manifest values into artifacts. This is done by
 * matching against a key within the execution context. Upon matching, we will convert the manifest
 * into an artifact and write the artifact in its place.
 */
public class ManifestMapStorageHandler implements ArtifactStorageHandler {
  private List<String> keys = List.of("manifests", "outputs.manifests");
  private final ApplicationFilter exclude;

  public ManifestMapStorageHandler(Map<String, List<ApplicationStorageFilter>> exclude) {
    this.exclude =
        new ApplicationFilter(exclude.get(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType()));
  }

  /** Manifest storage can only be handled on a list of maps. */
  @Override
  public boolean canHandle(Object v) {
    if (AuthenticatedRequest.getSpinnakerExecutionId().isEmpty() || this.exclude.shouldFilter()) {
      return false;
    }

    if (!(v instanceof Map)) {
      return false;
    }

    Map m = (Map) v;
    return this.keys.stream()
        .anyMatch(
            k -> {
              Object o = m.get(k);
              return isListOfManifests(o);
            });
  }

  private static boolean isListOfManifests(Object v) {
    if (!(v instanceof List)) {
      return false;
    }
    List l = (List) v;
    return !l.isEmpty() && isProperMapType(l.get(0));
  }

  private static boolean isProperMapType(Object o) {
    if (!(o instanceof Map)) {
      return false;
    }

    Map<?, ?> m = (Map<?, ?>) o;
    if (m.isEmpty()) {
      return false;
    }
    Map.Entry<?, ?> e = m.entrySet().stream().findFirst().get();
    return e.getKey() instanceof String;
  }

  @Override
  public <V> V handle(ArtifactStore store, V v, ObjectMapper objectMapper) {
    return (V) handleMap(store, (Map<?, ?>) v);
  }

  private <K, V> Map<K, V> handleMap(ArtifactStore store, Map<K, V> v) {
    Map<K, V> m = new HashMap<>(v);
    this.keys.forEach(
        k -> {
          V value = m.get(k);
          V temp = storeMaps(store, value);
          if (temp != value) {
            m.put((K) k, temp);
          }
        });

    return m;
  }

  private <V> V storeMaps(ArtifactStore store, V v) {
    if (v == null) {
      return null;
    }

    List<Map<String, Object>> manifests = (List<Map<String, Object>>) v;
    if (manifests.size() > 0 && EntityHelper.alreadyStored(manifests.get(0))) {
      // if manifests are already stored, we can assume that the rest are and just return early
      return v;
    }

    List<Map<String, Object>> stored = new ArrayList<>(manifests);
    for (int i = 0; i < manifests.size(); i++) {
      stored.set(i, this.convert(store, manifests.get(i)));
    }
    // We can cast directly to V for the same reasons as above, because we know V is a
    // List<Map<String, Object>>
    return (V) stored;
  }

  private <T> T convert(ArtifactStore store, T t) {
    Map<?, ?> v = (Map<?, ?>) t;
    if (EntityHelper.alreadyStored(v)) {
      return t;
    }

    Artifact artifact = EntityHelper.toArtifact(v, ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType());
    Artifact stored = store.store(artifact, ArtifactTypeDecorator.toRemote(artifact));
    if (ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType().equals(stored.getType())) {
      return t;
    }
    // We can cast directly to T due to our check of canHandle.
    return (T) EntityHelper.toMap(stored);
  }
}
