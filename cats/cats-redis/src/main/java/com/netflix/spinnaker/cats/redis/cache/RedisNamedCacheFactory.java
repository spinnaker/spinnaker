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

package com.netflix.spinnaker.cats.redis.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.NamedCacheFactory;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.redis.JedisSource;

public class RedisNamedCacheFactory implements NamedCacheFactory {

    private final JedisSource jedisSource;
    private final ObjectMapper objectMapper;

    public RedisNamedCacheFactory(JedisSource jedisSource, ObjectMapper objectMapper) {
        this.jedisSource = jedisSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public WriteableCache getCache(String name) {
        return new RedisCache(name, jedisSource, objectMapper);
    }
}
