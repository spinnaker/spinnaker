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
 * ManifestStorageHandler is used to convert manifest values into artifacts. This is done by
 * matching against a key within the execution context. Upon matching, we will convert the manifest
 * into an artifact and write the artifact in its place.
 */
public class ManifestStorageCollectionHandler implements ArtifactStoragePropertyHandler {
  private List<String> keys = List.of("manifests");
  private final ApplicationFilter exclude;

  public ManifestStorageCollectionHandler(Map<String, List<ApplicationStorageFilter>> exclude) {
    this.exclude =
        new ApplicationFilter(exclude.get(ArtifactTypes.EMBEDDED_MAP_BASE64.getMimeType()));
  }

  private <K, V> Map<K, V> handleMap(ArtifactStore store, Map<K, V> v) {
    return this.convert(store, v);
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
