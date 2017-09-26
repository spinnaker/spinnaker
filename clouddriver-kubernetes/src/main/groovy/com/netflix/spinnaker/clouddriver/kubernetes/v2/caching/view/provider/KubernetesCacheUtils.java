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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class KubernetesCacheUtils {
  private final Cache cache;

  public KubernetesCacheUtils(Cache cache) {
    this.cache = cache;
  }

  public Collection<CacheData> getAllKeys(String type) {
    return cache.getAll(type);
  }

  public CacheData getSingleEntry(String type, String key) {
    return cache.get(type, key);
  }

  public Collection<CacheData> getTransitiveRelationship(String from, List<String> sourceKeys, String to) {
    Collection<CacheData> sourceData = cache.getAll(from, sourceKeys, RelationshipCacheFilter.include(to));
    if (sourceData == null) {
      return Collections.emptyList();
    }

    return cache.getAll(to, sourceData.stream()
        .filter(Objects::nonNull)
        .map(CacheData::getRelationships)
        .filter(Objects::nonNull)
        .map(r -> r.get(to))
        .flatMap(Collection::stream)
        .collect(Collectors.toList()));
  }

  public Collection<CacheData> loadRelationshipsFromCache(Collection<CacheData> sources, String relationshipType) {
    List<String> keys = sources.stream()
        .filter(Objects::nonNull)
        .map(CacheData::getRelationships)
        .filter(Objects::nonNull)
        .map(r -> r.get(relationshipType))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());

    return cache.getAll(relationshipType, keys);
  }
}
