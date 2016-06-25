/*
 *
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.cache

import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND

//TODO - Move into core.
@Slf4j
class CacheResultBuilder {

  Long startTime

  CacheMutation onDemand = new CacheMutation()

  Map<String, NamespaceBuilder> namespaceBuilders = [:].withDefault {
    String ns -> new NamespaceBuilder(namespace: ns)
  }

  NamespaceBuilder namespace(String ns) {
    namespaceBuilders.get(ns)
  }

  DefaultCacheResult build() {
    Map<String, Collection<CacheData>> keep = [:]
    Map<String, Collection<String>> evict = [:]

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

