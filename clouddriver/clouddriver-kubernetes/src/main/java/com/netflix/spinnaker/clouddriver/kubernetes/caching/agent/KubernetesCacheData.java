/*
 * Copyright 2019 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.CacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * A collection of CacheItem entries used when building up the items being cached by the Kubernetes
 * caching agent. This class supports adding items as well as adding relationships between items.
 *
 * <p>Once all cache items and relationships have been added, calling toCacheData() will return a
 * Collection of CacheData entries that represent all added items and their relationships. The
 * operations supported on the class guarantee that the resulting Collection&lt;CacheData&gt; has
 * the following properties: (1) Each CacheData has a unique cache key, (2) all relationships
 * between CacheData items are bidirectional
 */
public class KubernetesCacheData {
  private final Map<CacheKey, CacheItem> items = new HashMap<>();

  /**
   * Add an item to the cache with specified key and attributes. If there is already an item with
   * the given key, the attributes are merged into the existing item's attributes (with the input
   * attributes taking priority).
   */
  public void addItem(CacheKey key, Map<String, Object> attributes) {
    CacheItem item = items.computeIfAbsent(key, CacheItem::new);
    item.getAttributes().putAll(attributes);
  }

  /**
   * Add a bidirectional relationship between two keys. If either of the keys is not yet in the
   * cache, an entry is created for that item with an empty map of attributes.
   */
  public void addRelationship(CacheKey a, CacheKey b) {
    items.computeIfAbsent(a, CacheItem::new).getRelationships().add(b);
    items.computeIfAbsent(b, CacheItem::new).getRelationships().add(a);
  }

  /**
   * Add a bidirectional relationships between key a and each of the keys in the Set b. If any of
   * the encountered keys is not in the cache, an entry is created for that item with an empty map
   * of attributes
   */
  public void addRelationships(CacheKey a, Set<CacheKey> b) {
    items.computeIfAbsent(a, CacheItem::new).getRelationships().addAll(b);
    b.forEach(k -> items.computeIfAbsent(k, CacheItem::new).getRelationships().add(a));
  }

  /** Return a List of CacheData entries representing the current items in the cache. */
  public List<CacheData> toCacheData() {
    return items.values().stream()
        .filter(item -> !item.omitItem())
        .map(CacheItem::toCacheData)
        .collect(Collectors.toList());
  }

  /**
   * Return a List of CacheData entries representing the current items in the cache, grouped by the
   * item's group.
   */
  public Map<String, Collection<CacheData>> toStratifiedCacheData() {
    return items.values().stream()
        .filter(item -> !item.omitItem())
        .collect(
            Collectors.groupingBy(
                item -> item.key.getGroup(),
                Collectors.mapping(
                    CacheItem::toCacheData, Collectors.toCollection(ArrayList::new))));
  }

  /**
   * An item being cached by the Kubernetes provider. This corresponds to a CacheData entry, but
   * stores the information in a format that is more efficient to manipulate as we build up the
   * cache data.
   *
   * <p>In particular:the cache key is stored as a Keys.CacheKey object (rather than serialized) so
   * we can access properties of the key without re-parsing it and the relationships are stored as a
   * flat Set&lt;Keys.CacheKey&gt; instead of as a Map&lt;String, Collection&lt;String&gt;&gt; so
   * that we can efficiently add relationships.
   *
   * <p>A CacheItem can be converted to its corresponding CacheData by calling toCacheData()
   */
  @Value
  private static class CacheItem {
    private final CacheKey key;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Set<CacheKey> relationships = new HashSet<>();

    private Map<String, Collection<String>> groupedRelationships() {
      Map<String, Collection<String>> groups = new HashMap<>();
      for (KubernetesKind kind : KubernetesCacheDataConverter.getStickyKinds()) {
        groups.put(kind.toString(), new HashSet<>());
      }
      for (CacheKey key : relationships) {
        groups.computeIfAbsent(key.getGroup(), k -> new HashSet<>()).add(key.toString());
      }
      return groups;
    }

    /**
     * given that we now have large caching agents that are authoritative for huge chunks of the
     * cache, it's possible that some resources (like events) still point to deleted resources.
     * These won't have any attributes, but if we add a cache entry here, the deleted item will
     * still be cached
     */
    public boolean omitItem() {
      return key instanceof InfrastructureCacheKey && attributes.isEmpty();
    }

    /** Convert this CacheItem to its corresponding CacheData object */
    public CacheData toCacheData() {
      int ttlSeconds;
      if (Keys.LogicalKind.isLogicalGroup(key.getGroup())) {
        // If we are inverting a relationship to create a cache data for either a cluster or an
        // application we
        // need to insert attributes to ensure the cache data gets entered into the cache.
        attributes.putIfAbsent("name", key.getName());
        ttlSeconds = KubernetesCacheDataConverter.getLogicalTtlSeconds();
      } else {
        ttlSeconds = KubernetesCacheDataConverter.getInfrastructureTtlSeconds();
      }
      return new DefaultCacheData(key.toString(), ttlSeconds, attributes, groupedRelationships());
    }
  }
}
