/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.cache

import com.google.common.collect.ImmutableSet
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import groovy.util.logging.Slf4j

import static com.google.common.collect.ImmutableSet.toImmutableSet
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.ON_DEMAND

@Slf4j
class CacheResultBuilder {

  Long startTime

  CacheMutation onDemand = new CacheMutation()

  Set<String> authoritativeTypes = ImmutableSet.of()

  CacheResultBuilder() {}

  /**
   * Create a CacheResultBuilder for the given dataTypes.
   *
   * Any authoritative types in dataTypes are guaranteed to be listed in the
   * output. If you say you are authoritative for "clusters", but don't include
   * any data under that namespace, an empty list will be included in the
   * result. (Whereas if you don't pass dataTypes to the constructor, "clusters"
   * will just be missing from the result if you don't specify any, and any
   * existing clusters will remain in the cache).
   */
  CacheResultBuilder(Collection<AgentDataType> dataTypes) {
    authoritativeTypes = dataTypes.stream()
      .filter({ dataType -> dataType.getAuthority() == AUTHORITATIVE })
      .map({ dataType -> dataType.getTypeName() })
      .collect(toImmutableSet())
  }

  Map<String, NamespaceBuilder> namespaceBuilders = [:].withDefault {
    String ns -> new NamespaceBuilder(namespace: ns)
  }

  NamespaceBuilder namespace(String ns) {
    namespaceBuilders.get(ns)
  }

  DefaultCacheResult build() {
    Map<String, Collection<CacheData>> keep = [:]
    Map<String, Collection<String>> evict = [:]

    authoritativeTypes.each { namespace ->
      keep[namespace] = []
    }
    if (!onDemand.toKeep.empty) {
      keep += [(ON_DEMAND.ns): onDemand.toKeep.values()]
    }
    if (!onDemand.toEvict.empty) {
      evict += [(ON_DEMAND.ns): onDemand.toEvict]
    }
    namespaceBuilders.each { String namespace, NamespaceBuilder nsBuilder ->
      def buildResult = nsBuilder.build()
      if (!buildResult.toKeep.empty) {
        keep += [(namespace): buildResult.toKeep.values()]
      }
      if (!buildResult.toEvict.empty) {
        evict += [(namespace): buildResult.toEvict]
      }
    }

    new DefaultCacheResult(keep, evict)
  }

  class NamespaceBuilder {
    String namespace

    private Map<String, CacheDataBuilder> toKeep = [:].withDefault {
      String id -> new CacheDataBuilder(id: id)
    }

    private List<String> toEvict = []

    CacheDataBuilder keep(String key) {
      toKeep.get(key)
    }

    int keepSize() {
      toKeep.size()
    }

    CacheMutation build() {
      def keepers = toKeep.collectEntries { k, b -> [(k): b.build()] }
      new CacheMutation(toKeep: keepers, toEvict: toEvict)
    }
  }

  class CacheMutation {
    // CacheData.id -> CacheData
    Map<String, CacheData> toKeep = [:]
    List<String> toEvict = []
  }

  class CacheDataBuilder {
    String id = ''
    int ttlSeconds = -1
    Map<String, Object> attributes = [:]
    Map<String, Collection<String>> relationships = [:].withDefault({ _ -> [] as Set })

    public DefaultCacheData build() {
      new DefaultCacheData(id, ttlSeconds, attributes, relationships)
    }
  }
}
