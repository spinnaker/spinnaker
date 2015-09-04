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

package com.netflix.spinnaker.cats.provider;

import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;

import java.util.Collection;

public interface ProviderCache extends Cache {
    void putCacheResult(String source, Collection<String> authoritativeTypes, CacheResult cacheResult);
    void putCacheData(String type, CacheData cacheData);

    Collection<CacheData> getAll(String type, Collection<String> identifiers);

    void evictDeletedItems(String type, Collection<String> ids);
}
