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

package com.netflix.spinnaker.clouddriver.yandex;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.AgentDataType.Authority;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
public class CacheResultBuilder {
  private Long startTime;
  private CacheMutation onDemand = new CacheMutation();
  private Set<String> authoritativeTypes = ImmutableSet.of();
  private Map<String, NamespaceBuilder> namespaceBuilders = new HashMap<>();

  public CacheResultBuilder() {}

  /**
   * Create a CacheResultBuilder for the given dataTypes.
   *
   * <p>Any authoritative types in dataTypes are guaranteed to be listed in the output. If you say
   * you are authoritative for "clusters", but don't include any data under that namespace, an empty
   * list will be included in the result. (Whereas if you don't pass dataTypes to the constructor,
   * "clusters" will just be missing from the result if you don't specify any, and any existing
   * clusters will remain in the cache).
   */
  public CacheResultBuilder(Collection<AgentDataType> dataTypes) {
    authoritativeTypes =
        dataTypes.stream()
            .filter(dataType -> dataType.getAuthority().equals(Authority.AUTHORITATIVE))
            .map(AgentDataType::getTypeName)
            .collect(toSet());
  }

  public NamespaceBuilder namespace(String ns) {
    return namespaceBuilders.computeIfAbsent(ns, NamespaceBuilder::new);
  }

  public DefaultCacheResult build() {
    Map<String, Collection<CacheData>> keep = new HashMap<>();
    Map<String, Collection<String>> evict = new HashMap<>();
    namespaceBuilders.forEach(
        (namespace, nsBuilder) -> {
          CacheMutation buildResult = nsBuilder.build();
          if (!buildResult.getToKeep().isEmpty()) {
            keep.put(namespace, buildResult.getToKeep().values());
          }

          if (!buildResult.getToEvict().isEmpty()) {
            evict.put(namespace, buildResult.getToEvict());
          }
        });
    return new DefaultCacheResult(keep, evict);
  }

  @Data
  @RequiredArgsConstructor
  public class NamespaceBuilder {
    private final String namepace;
    private final Map<String, CacheDataBuilder> toKeep = new HashMap<>();
    private List<String> toEvict = new ArrayList<>();

    public CacheDataBuilder keep(String key) {
      return toKeep.computeIfAbsent(key, CacheDataBuilder::new);
    }

    public CacheMutation build() {
      Map<String, CacheData> keepers =
          toKeep.entrySet().stream().collect(toMap(Map.Entry::getKey, o -> o.getValue().build()));
      return new CacheMutation(keepers, toEvict);
    }
  }

  @AllArgsConstructor
  @Data
  public class CacheMutation {
    private final Map<String, CacheData> toKeep;
    private final List<String> toEvict;

    CacheMutation() {
      this(new HashMap<>(), new ArrayList<>());
    }
  }

  @RequiredArgsConstructor
  @Data
  public class CacheDataBuilder {
    private final String id;
    private int ttlSeconds = -1;
    private Map<String, Object> attributes = new HashMap<>();
    private Map<String, Collection<String>> relationships = new HashMap<>();

    public DefaultCacheData build() {
      return new DefaultCacheData(id, ttlSeconds, attributes, relationships);
    }
  }
}
