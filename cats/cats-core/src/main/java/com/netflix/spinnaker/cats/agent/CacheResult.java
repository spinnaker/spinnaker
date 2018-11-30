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

package com.netflix.spinnaker.cats.agent;

import com.netflix.spinnaker.cats.cache.CacheData;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * The result of a CachingAgent run.
 */
public interface CacheResult {
  /**
   * @return The CacheDatas to cache, keyed by item type.
   */
  Map<String, Collection<CacheData>> getCacheResults();

  /**
   * Provides a means to explicitly evict items as a result of a CachingAgent execution.
   *
   * Note: Eviction will already occur based on the values in getCacheResults for all the types
   *       that the CachingAgent authoritatively caches - this collection is for additional items
   *       that were potentially cached out of band of a complete caching run.
   * @return The ids of items that should be explicitly evicted.
   */
  default Map<String, Collection<String>> getEvictions() { return Collections.emptyMap(); }

  default Map<String, Object> getIntrospectionDetails() {
    return Collections.emptyMap();
  }
}
