/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.cats.cache;

import java.util.Collection;
import java.util.Map;

/**
 * CacheData is stored in a Cache. Attributes are facts about the CacheData that can be updated by
 * CachingAgents. Relationships are links to other CacheData.
 *
 * <p>Note: Not all caches may support a per record ttl
 */
public interface CacheData {
  String getId();

  /** @return The ttl (in seconds) for this CacheData */
  int getTtlSeconds();

  Map<String, Object> getAttributes();

  /**
   * @return relationships for this CacheData, keyed by type returning a collection of ids for that
   *     type
   */
  Map<String, Collection<String>> getRelationships();
}
