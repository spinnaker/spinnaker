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

package com.netflix.spinnaker.clouddriver.docker.registry.cache

import com.netflix.spinnaker.cats.cache.DefaultCacheData

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class DefaultCacheDataBuilder {
  String id = ''
  int ttlSeconds = -1
  Map<String, Object> attributes = [:]
  Map<String, Collection<String>> relationships = [:].withDefault({ _ -> [] as Set })

  public DefaultCacheData build() {
    new DefaultCacheData(id, ttlSeconds, attributes, relationships)
  }

  public static ConcurrentMap<String, DefaultCacheDataBuilder> defaultCacheDataBuilderMap() {
    return new ConcurrentHashMap<String, DefaultCacheDataBuilder>()
  }
}
