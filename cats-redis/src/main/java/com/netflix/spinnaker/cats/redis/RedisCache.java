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

package com.netflix.spinnaker.cats.redis;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;

import java.util.Collection;

public class RedisCache implements WriteableCache {
    @Override
    public void merge(String type, CacheData cacheData) {

    }

    @Override
    public void mergeAll(String type, Collection<CacheData> items) {

    }

    @Override
    public void evict(String type, String id) {

    }

    @Override
    public void evictAll(String type, Collection<String> ids) {

    }

    @Override
    public CacheData get(String type, String id) {
        return null;
    }

    @Override
    public Collection<CacheData> getAll(String type) {
        return null;
    }
}
