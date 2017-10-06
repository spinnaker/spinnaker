/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class KubernetesCacheUtils {
  private final Cache cache;

  @Autowired
  public KubernetesCacheUtils(Cache cache) {
    this.cache = cache;
  }

  public Collection<CacheData> getAllKeys(String type) {
    return cleanupCollection(cache.getAll(type));
  }

  public Collection<String> getAllKeysMatchingPattern(String type, String key) {
    return cleanupCollection(cache.filterIdentifiers(type, key));
  }

  public Collection<CacheData> getAllDataMatchingPattern(String type, String key) {
    return cleanupCollection(cache.getAll(type, getAllKeysMatchingPattern(type, key)));
  }

  public Optional<CacheData> getSingleEntry(String type, String key) {
    CacheData result = cache.get(type, key);
    return result == null ? Optional.empty() : Optional.of(result);
  }

  public Collection<CacheData> getTransitiveRelationship(String from, List<String> sourceKeys, String to) {
    Collection<CacheData> sourceData = cleanupCollection(cache.getAll(from, sourceKeys, RelationshipCacheFilter.include(to)));
    return cache.getAll(to, sourceData.stream()
        .map(CacheData::getRelationships)
        .filter(Objects::nonNull)
        .map(r -> r.get(to))
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .collect(Collectors.toList()));
  }

  public Collection<CacheData> loadRelationshipsFromCache(Collection<CacheData> sources, String relationshipType) {
    List<String> keys = cleanupCollection(sources).stream()
        .map(CacheData::getRelationships)
        .filter(Objects::nonNull)
        .map(r -> r.get(relationshipType))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());

    return cleanupCollection(cache.getAll(relationshipType, keys));
  }

  private <T> Collection<T> cleanupCollection(Collection<T> items) {
    if (items == null) {
      return new ArrayList<>();
    }

    return items.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
