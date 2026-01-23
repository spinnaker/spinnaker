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

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypeDecorator;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.filters.ApplicationFilter;
import com.netflix.spinnaker.kork.artifacts.artifactstore.filters.ApplicationStorageFilter;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles the storage of manifest collections (specifically Sets) as artifacts.
 *
 * <p>This handler specifically targets Set collections of manifests and converts each manifest in
 * the Set to an artifact. Unlike {@link ManifestMapStorageHandler} which handles Lists of
 * manifests, this handler processes Sets of manifests that appear as properties in beans.
 */
public class ManifestStorageCollectionHandler implements ArtifactStoragePropertyHandler {
  /** The property names that this handler will process */
  private List<String> keys = List.of("manifests");

  /** Filter to determine which applications should be excluded from artifact storage */
  private final ApplicationFilter exclude;

  /**
   * Constructs a handler with the specified exclusion filters.
   *
   * @param exclude Map of artifact types to application filters that determine which applications
   *     should be excluded from artifact storage
   */
  public ManifestStorageCollectionHandler(Map<String, List<ApplicationStorageFilter>> exclude) {
    this.exclude =
        new ApplicationFilter(exclude.get(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType()));
  }

  /**
   * Processes a map by converting it to an artifact and storing it.
   *
   * @param store The artifact store to use for storage
   * @param v The map to convert and store
   * @return The processed map, either as an artifact reference or the original if storage was
   *     skipped
   */
  private <K, V> Map<K, V> handleMap(ArtifactStore store, Map<K, V> v) {
    return this.convert(store, v);
  }

  /**
   * Converts an object to an artifact and stores it.
   *
   * @param store The artifact store to use for storage
   * @param t The object to convert and store (must be a Map)
   * @return The processed object, either as an artifact reference or the original if storage was
   *     skipped
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

  /**
   * Determines if this handler can process the given property.
   *
   * <p>This method checks several conditions to determine if the property represents a valid
   * manifest collection that should be stored as artifacts:
   *
   * <ol>
   *   <li>Verifies there is an active Spinnaker execution context
   *   <li>Checks if the current application is excluded from manifest storage
   *   <li>Confirms the property name matches one of the predefined keys (e.g., "manifests")
   *   <li>Ensures the value is a non-empty Set
   *   <li>Verifies the first element in the Set is a Map
   *   <li>Confirms the Map hasn't already been stored as an artifact
   * </ol>
   *
   * @param property The bean property to check
   * @param value The value of the property
   * @return true if this handler can process the property, false otherwise
   */
  @Override
  public boolean canHandleProperty(BeanProperty property, Object value) {
    if (AuthenticatedRequest.getSpinnakerExecutionId().isEmpty() || this.exclude.shouldFilter()) {
      return false;
    }

    String propertyName = property.getName();
    Collection<?> v = (Collection<?>) value;
    if (!(v instanceof Set && v.size() != 0)) {
      return false;
    }

    Object obj = v.iterator().next();
    if (!(obj instanceof Map)) {
      return false;
    }
    // Good ol' clouddriver sometimes stores manifests as a list and other times as a set.
    return this.keys.stream().anyMatch(k -> k.equals(propertyName))
        && !EntityHelper.alreadyStored((Map) obj);
  }

  /**
   * Processes a property by converting each Map in the Set to an artifact and storing it.
   *
   * @param store The artifact store to use for storage
   * @param property The bean property being processed
   * @param v The value of the property (a Set of Maps)
   * @param objectMapper The object mapper to use for serialization
   * @return A new Set containing the processed elements, with Maps replaced by artifact references
   */
  @Override
  public <T> T handleProperty(
      ArtifactStore store, BeanProperty property, T v, ObjectMapper objectMapper) {
    // We can cast this directly given that canHandleProperty was called which ensures that this is
    // a set.
    Set<?> arr = (Set<?>) v;
    Set<Object> hashSet = new HashSet<>();
    for (Iterator<?> it = arr.iterator(); it.hasNext(); ) {
      Object elem = it.next();
      if (elem instanceof Map) {
        Map<?, ?> m = (Map<?, ?>) elem;
        elem = handleMap(store, m);
      }

      hashSet.add(elem);
    }
    return (T) hashSet;
  }
}
