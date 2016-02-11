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

import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.DefaultCacheData

class CacheResultBuilder {

  Map<String, NamespaceBuilder> namespaceBuilders = [:].withDefault {
    String ns -> new NamespaceBuilder(namespace: ns)
  }

  NamespaceBuilder namespace(String ns) {
    namespaceBuilders.get(ns)
  }

  DefaultCacheResult build() {
    new DefaultCacheResult(namespaceBuilders.collectEntries(new HashMap<String, List<DefaultCacheData>>()) {
      [(it.key): it.value.build()]
    })
  }

  class NamespaceBuilder {
    String namespace
    Map<String, CacheDataBuilder> cacheDataBuilders = [:].withDefault {
      String id -> new CacheDataBuilder(id: id)
    }

    CacheDataBuilder get(String key) {
      cacheDataBuilders.get(key)
    }

    int size() {
      cacheDataBuilders.size()
    }

    List<DefaultCacheData> build() {
      cacheDataBuilders.values().collect { b -> b.build() }
    }

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
