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
import lombok.Getter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * An immutable CacheResult.
 */
public class DefaultCacheResult implements CacheResult {
    private final Map<String, Collection<CacheData>> cacheResults;
    private final Map<String, Collection<String>> evictions;
    @Getter
    private final Map<String, Object> introspectionDetails;

    public DefaultCacheResult(Map<String, Collection<CacheData>> cacheResults) {
      this(cacheResults, new HashMap<>());
    }
    public DefaultCacheResult(Map<String, Collection<CacheData>> cacheResults, Map<String, Collection<String>> evictions) {
      this(cacheResults, evictions, new HashMap<>());
    }

    public DefaultCacheResult(Map<String, Collection<CacheData>> cacheResults, Map<String, Collection<String>> evictions, Map<String, Object> introspectionDetails) {
        this.cacheResults = cacheResults;
        this.evictions = evictions;
        this.introspectionDetails = introspectionDetails;
    }

    @Override
    public Map<String, Collection<CacheData>> getCacheResults() {
        return cacheResults;
    }

    @Override
    public Map<String, Collection<String>> getEvictions() {
        return evictions;
    }
}
