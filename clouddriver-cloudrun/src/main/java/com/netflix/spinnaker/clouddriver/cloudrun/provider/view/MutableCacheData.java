/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.provider.view;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.cats.cache.CacheData;
import java.util.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class MutableCacheData implements CacheData {
  final String id;
  final Map<String, Collection<String>> relationships = new HashMap<>();
  final Map<String, Object> attributes = new HashMap<>();
  final int ttlSeconds = -1;

  public MutableCacheData(String id) {
    this.id = id;
  }

  @JsonCreator
  public MutableCacheData(
      @JsonProperty("id") String id,
      @JsonProperty("attributes") Map<String, Object> attributes,
      @JsonProperty("relationships") Map<String, Collection<String>> relationships) {
    this(id);
    this.attributes.putAll(attributes);
    this.relationships.putAll(relationships);
  }

  public static Map<String, MutableCacheData> mutableCacheMap() {
    Map<String, MutableCacheData> cacheMap = new LinkedHashMap<String, MutableCacheData>();
    return cacheMap;
  }
}
