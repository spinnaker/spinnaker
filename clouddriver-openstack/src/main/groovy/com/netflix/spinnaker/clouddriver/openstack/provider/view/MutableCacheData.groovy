/*
 * Copyright 2016 Target Inc.
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
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.cats.cache.CacheData

/* TODO(lwander) this was taken from the netflix cluster caching, and should probably be shared between all providers. */

class MutableCacheData implements CacheData {
  final String id
  int ttlSeconds = -1
  final Map<String, Object> attributes = [:]
  final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }

  public MutableCacheData(String id) {
    this.id = id
  }

  @JsonCreator
  public MutableCacheData(@JsonProperty("id") String id,
                          @JsonProperty("attributes") Map<String, Object> attributes,
                          @JsonProperty("relationships") Map<String, Collection<String>> relationships) {
    this(id);
    this.attributes.putAll(attributes);
    this.relationships.putAll(relationships);
  }

  public static Map<String, MutableCacheData> mutableCacheMap() {
    return [:].withDefault { String id -> new MutableCacheData(id) }
  }
}
