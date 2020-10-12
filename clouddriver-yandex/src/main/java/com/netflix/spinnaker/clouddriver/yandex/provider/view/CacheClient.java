/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CacheFilter;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CacheClient<T> {
  private final ObjectMapper objectMapper;
  private final Cache cacheView;
  private final Keys.Namespace namespace;
  private final Class<T> clazz;

  public CacheClient(
      Cache cacheView, ObjectMapper objectMapper, Keys.Namespace namespace, Class<T> clazz) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
    this.namespace = namespace;
    this.clazz = clazz;
  }

  public Optional<T> findOne(String pattern) {
    return findAll(pattern).stream().findFirst();
  }

  public List<T> getAll(Collection<String> identifiers) {
    return cacheView.getAll(namespace.getNs(), identifiers).stream()
        .map(cacheData -> convert(cacheData.getAttributes()))
        .collect(Collectors.toList());
  }

  public Optional<T> get(String identifier) {
    return Optional.ofNullable(cacheView.get(namespace.getNs(), identifier))
        .map(cacheData -> convert(cacheData.getAttributes()));
  }

  public List<T> findAll(String pattern) {
    Collection<String> identifiers = filterIdentifiers(pattern);
    return getAll(identifiers);
  }

  public Set<T> getAll(String cloudFilter) {
    Collection<String> keys = cacheView.filterIdentifiers(namespace.getNs(), cloudFilter);
    return cacheView.getAll(namespace.getNs(), keys).stream()
        .map(cacheData -> convert(cacheData.getAttributes()))
        .collect(Collectors.toSet());
  }

  public Collection<String> filterIdentifiers(String key) {
    return cacheView.filterIdentifiers(namespace.getNs(), key);
  }

  private T convert(Map<String, Object> attributes) {
    return objectMapper.convertValue(attributes, clazz);
  }

  public Collection<String> getRelationKeys(String key, Keys.Namespace relationship) {
    CacheFilter filter = RelationshipCacheFilter.include(relationship.getNs());
    CacheData cacheData = cacheView.get(namespace.getNs(), key, filter);
    return cacheData.getRelationships().getOrDefault(relationship.getNs(), Collections.emptySet());
  }

  public <R> Set<R> getRelationEntities(String key, Keys.Namespace relationship, Class<R> clazz) {
    CacheFilter filter = RelationshipCacheFilter.include(relationship.getNs());
    CacheData cacheData = cacheView.get(namespace.getNs(), key, filter);
    Collection<String> keys =
        cacheData.getRelationships().getOrDefault(relationship.getNs(), Collections.emptySet());
    return cacheView.getAll(relationship.getNs(), keys).stream()
        .map(c -> objectMapper.convertValue(c.getAttributes(), clazz))
        .collect(Collectors.toSet());
  }
}
