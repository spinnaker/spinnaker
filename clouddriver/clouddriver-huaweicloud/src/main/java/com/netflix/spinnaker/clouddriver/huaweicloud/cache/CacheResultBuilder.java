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

import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

public class CacheResultBuilder {

  private final long startTime;

  private final CacheMutation onDemand = new CacheMutation();

  private final Map<String, NamespaceCache> namespaceBuilders = new HashMap();

  public CacheResultBuilder(long startTime) {
    this.startTime = startTime;
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
