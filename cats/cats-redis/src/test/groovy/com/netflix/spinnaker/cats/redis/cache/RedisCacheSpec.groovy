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

package com.netflix.spinnaker.cats.redis.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.WriteableCacheSpec
import com.netflix.spinnaker.cats.redis.JedisPoolSource
import com.netflix.spinnaker.cats.redis.test.LocalRedisCheck
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.IgnoreIf
import spock.lang.Unroll

@IgnoreIf({ LocalRedisCheck.redisUnavailable() })
class RedisCacheSpec extends WriteableCacheSpec {

    @Override
    Cache getSubject() {
        def pool = new JedisPool("localhost", 6379)
        def source = new JedisPoolSource(pool)
        Jedis jedis
        try {
            jedis = source.jedis
            jedis.flushAll()
        } finally {
            jedis?.close()
        }

        def mapper = new ObjectMapper();
        return new RedisCache('test', source, mapper)
    }

    @Unroll
    def 'attribute datatype handling #description'() {
        setup:
        def mergeData = createData('foo', [test: value], [:])
        cache.merge('test', mergeData)

        when:
        def cacheData = cache.get('test', 'foo')

        then:
        cacheData != null
        cacheData.attributes.test == expected

        where:
        value                      | expected                   | description
        null                       | null                       | "null"
        1                          | 1                          | "Integer"
        2.0f                       | 2.0f                       | "Float"
        "Bacon"                    | "Bacon"                    | "String"
        true                       | true                       | "Boolean"
        ['one', 'two']             | ['one', 'two']             | "Primitive list"
        [key: 'value', key2: 10]   | [key: 'value', key2: 10]   | "Map"
        new Bean('value', 10)      | [key: 'value', key2: 10]   | "Java object"
        [key: 'value', key2: null] | [key: 'value']             | "Map with null"
        new Bean('value', null)    | [key: 'value', key2: null] | "Java object with null"
    }

    @Unroll
    def 'cache data will expire if ttl specified'() {
        setup:
        def mergeData = new DefaultCacheData('ttlTest', ttl, [test: 'test'], [:])
        cache.merge('test', mergeData);

        when:
        def cacheData = cache.get('test', 'ttlTest')

        then:
        cacheData.id == mergeData.id

        when:
        Thread.sleep(Math.abs(ttl) * 1500)
        cacheData = cache.get('test', 'ttlTest')

        then:
        cacheData?.id == (ttl > 0 ? null : mergeData.id)

        where:
        ttl || _
        -1  || _
        1   || _

    }

    private static class Bean {
        String key
        Integer key2

        Bean(String key, Integer key2) {
            this.key = key
            this.key2 = key2
        }
    }
}
