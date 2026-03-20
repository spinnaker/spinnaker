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
 * Handles the storage of Kubernetes manifests as artifacts.
 *
 * <p>This handler specifically targets Maps containing manifest lists (typically under keys like
 * "manifests" or "outputs.manifests"). When such a structure is detected, each manifest in the list
 * is converted to an artifact and stored separately in the artifact store.
 *
 * <p>The original manifest content is replaced with a reference to the stored artifact, which
 * significantly reduces the size of pipeline execution contexts and improves performance when these
 * contexts need to be serialized/deserialized or transmitted between services.
 *
 * <p>This handler is particularly important for Kubernetes deployments where manifests can be large
 * and complex, potentially causing performance issues when stored directly in pipeline execution
 * contexts.
 */
public class ManifestMapStorageHandler implements ArtifactStorageHandler {
  /** The property keys that this handler will look for in maps */
  private List<String> keys = List.of("manifests", "outputs.manifests");

  /** Filter to determine which applications should be excluded from artifact storage */
  private final ApplicationFilter exclude;

  /**
   * Constructs a handler with the specified exclusion filters.
   *
   * @param exclude Map of artifact types to application filters that determine which applications
   *     should be excluded from manifest storage
   */
  public ManifestMapStorageHandler(Map<String, List<ApplicationStorageFilter>> exclude) {
    this.exclude =
        new ApplicationFilter(exclude.get(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType()));
  }

  /**
   * Determines if this handler can process the given object.
   *
   * <p>This method checks several conditions to determine if the object represents a valid manifest
   * that should be stored as an artifact:
   *
   * <ol>
   *   <li>Verifies there is an active Spinnaker execution context
   *   <li>Checks if the current application is excluded from manifest storage
   *   <li>Confirms the object is a Map
   *   <li>Verifies the Map contains one of the predefined manifest keys (e.g., "manifests")
   *   <li>Ensures the value at that key is a non-empty list of valid manifest maps
   * </ol>
   *
   * <p>If all conditions are met, the handler will process the manifests by converting them to
   * artifacts and storing them separately.
   *
   * @param v The object to check
   * @return true if this handler can process the object, false otherwise
   */
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

  /**
   * Checks if the object is a non-empty list of manifest maps.
   *
   * <p>A valid manifest list must:
   *
   * <ul>
   *   <li>Be a List instance
   *   <li>Contain at least one element
   *   <li>Have its first element be a proper map type (non-empty Map with String keys)
   * </ul>
   *
   * @param v The object to check
   * @return true if the object is a valid list of manifests, false otherwise
   */
  private static boolean isListOfManifests(Object v) {
    if (!(v instanceof List)) {
      return false;
    }
    List l = (List) v;
    return !l.isEmpty() && isProperMapType(l.get(0));
  }

  /**
   * Verifies if an object is a proper map type for manifest storage.
   *
   * <p>A proper map type must:
   *
   * <ul>
   *   <li>Be a Map instance
   *   <li>Not be empty
   *   <li>Have String keys (which is required for proper serialization and deserialization)
   * </ul>
   *
   * @param o The object to check
   * @return true if the object is a proper map type, false otherwise
   */
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

  /**
   * Processes a map by converting manifest lists to artifact references.
   *
   * @param store The artifact store to use for storage
   * @param v The object to process (must be a Map)
   * @param objectMapper The object mapper to use for serialization if needed
   * @return The processed map with manifest lists replaced by artifact references
   */
  @Override
  public <V> V handle(ArtifactStore store, V v, ObjectMapper objectMapper) {
    return (V) handleMap(store, (Map<?, ?>) v);
  }

  /**
   * Processes a map by looking for manifest keys and storing their contents as artifacts.
   *
   * @param store The artifact store to use for storage
   * @param v The map to process
   * @return A new map with manifest lists replaced by artifact references
   */
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

  /**
   * Processes a list of manifests by converting each manifest to an artifact.
   *
   * @param store The artifact store to use for storage
   * @param v The list of manifests to process
   * @return A new list with manifests replaced by artifact references
   */
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

  /**
   * Converts a manifest map to an artifact and stores it.
   *
   * @param store The artifact store to use for storage
   * @param t The manifest map to convert and store
   * @return Either the original map if storage was skipped, or a reference map to the stored
   *     artifact
   */
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
