/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.huaweicloud.cache;

import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.AgentDataType.Authority;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

public class CacheResultBuilder {

  private final long startTime;

  private final CacheMutation onDemand = new CacheMutation();

  private final Set<String> authoritativeTypes;

  private final Map<String, NamespaceCache> namespaceBuilders = new HashMap();

  public CacheResultBuilder(long startTime) {
    this(startTime, Collections.emptyList());
  }

  /**
   * Any authoritative type in dataTypes is guaranteed to have a key in the built result, even with
   * zero items, so that SqlCache's existingIds-minus-currentIds eviction diff (which only runs for
   * types present in the result) can clean up a type's last cached item once nothing live remains
   * for it.
   */
  public CacheResultBuilder(long startTime, Collection<AgentDataType> dataTypes) {
    this.startTime = startTime;
    this.authoritativeTypes =
        dataTypes.stream()
            .filter(dataType -> dataType.getAuthority().equals(Authority.AUTHORITATIVE))
            .map(AgentDataType::getTypeName)
            .collect(Collectors.toSet());
  }

  public long getStartTime() {
    return startTime;
  }

  public CacheMutation getOnDemand() {
    return this.onDemand;
  }

  public NamespaceCache getNamespaceCache(String ns) {
    if (namespaceBuilders.containsKey(ns)) {
      return namespaceBuilders.get(ns);
    }
    NamespaceCache cache = new NamespaceCache(ns);
    namespaceBuilders.put(ns, cache);
    return cache;
  }

  public DefaultCacheResult build() {
    Map<String, Collection<String>> evict = new HashMap();
    Map<String, Collection<CacheData>> keep = new HashMap();

    authoritativeTypes.forEach(namespace -> keep.put(namespace, new ArrayList<>()));

    if (!onDemand.getToKeep().isEmpty()) {
      keep.put(Keys.Namespace.ON_DEMAND.ns, onDemand.getToKeep().values());
    }

    if (!onDemand.getToEvict().isEmpty()) {
      evict.put(Keys.Namespace.ON_DEMAND.ns, onDemand.getToEvict());
    }

    namespaceBuilders.forEach(
        (namespace, item) -> {
          if (!item.getToKeep().isEmpty()) {
            keep.put(namespace, item.getCacheDatas());
          }

          if (!item.getToEvict().isEmpty()) {
            evict.put(namespace, item.getToEvict());
          }
        });

    return new DefaultCacheResult(keep, evict);
  }

  @Getter
  public static class CacheMutation {
    private final List<String> toEvict = new ArrayList();

    private final Map<String, CacheData> toKeep = new HashMap();
  }

  @Getter
  public static class NamespaceCache {
    private final String namespace;

    private final List<String> toEvict = new ArrayList();

    private final Map<String, CacheDataBuilder> toKeep = new HashMap();

    public NamespaceCache(String namespace) {
      this.namespace = namespace;
    }

    public CacheDataBuilder getCacheDataBuilder(String key) {
      if (toKeep.containsKey(key)) {
        return toKeep.get(key);
      }

      CacheDataBuilder builder = new CacheDataBuilder(key);
      toKeep.put(key, builder);
      return builder;
    }

    public Collection<CacheData> getCacheDatas() {
      Collection<CacheData> result = new ArrayList(toKeep.size());

      toKeep.forEach((k, item) -> result.add(item.build()));

      return result;
    }
  }

  public static class CacheDataBuilder {
    private final String id;
    private int ttlSeconds = -1;
    private Map<String, Object> attributes = new HashMap();
    private final Map<String, Collection<String>> relationships = new HashMap();

    public CacheDataBuilder(String id) {
      this.id = id;
    }

    public DefaultCacheData build() {
      return new DefaultCacheData(id, ttlSeconds, attributes, relationships);
    }

    public void setTtlSeconds(int value) {
      this.ttlSeconds = value;
    }

    public Map<String, Object> getAttributes() {
      return this.attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
      this.attributes = attributes;
    }

    public Map<String, Collection<String>> getRelationships() {
      return relationships;
    }
  }
}
