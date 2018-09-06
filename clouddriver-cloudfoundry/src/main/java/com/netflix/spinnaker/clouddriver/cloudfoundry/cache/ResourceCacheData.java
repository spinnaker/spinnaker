/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.cache;

import com.netflix.spinnaker.cats.cache.CacheData;
import lombok.Getter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


@Getter
public class ResourceCacheData implements CacheData {
  final String id;
  final Map<String, Collection<String>> relationships;
  final Map<String, Object> attributes;
  final int ttlSeconds = -1;

  public ResourceCacheData(String id, Object resource, Map<String, Collection<String>> relationships) {
    this.id = id;

    this.attributes = new HashMap<>();
    this.attributes.put("resource", resource);

    this.relationships = relationships;
  }
}
