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
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.cache.WriteableCacheSpec
import com.netflix.spinnaker.cats.redis.cache.RedisCache.CacheMetrics
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

class RedisCacheSpec extends WriteableCacheSpec {
  static int MAX_MSET_SIZE = 2
  static int MAX_MERGE_COUNT = 1

  CacheMetrics cacheMetrics = Mock()
  JedisPool pool

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Override
  Cache getSubject() {
    if (!embeddedRedis) {
      embeddedRedis = EmbeddedRedis.embed()
    }
    pool = embeddedRedis.pool as JedisPool
    Jedis jedis
    try {
      jedis = pool.resource
      jedis.flushAll()
    } finally {
      jedis?.close()
    }

    def mapper = new ObjectMapper();
    return new RedisCache('test', new JedisClientDelegate(pool), mapper, RedisCacheOptions.builder().maxMset(MAX_MSET_SIZE).maxMergeBatch(MAX_MERGE_COUNT).build(), cacheMetrics)
  }

  def 'a cached value does not exist until it has attributes'() {
    setup:
    populateOne('foo', 'bar', createData('bar', [:]))

    expect:
    cache.get('foo', 'bar') == null
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

  def 'verify MSET chunking behavior (> MAX_MSET_SIZE)'() {
    setup:
    ((WriteableCache) cache).mergeAll('foo', [createData('bar'), createData('baz'), createData('bam')])

    expect:
    cache.getIdentifiers('foo').sort() == ['bam', 'bar', 'baz']
  }

  def 'should fail if maxMsetSize is not even'() {
    when:
    RedisCacheOptions.builder().maxMset(7).build()

    then:
    thrown(IllegalArgumentException)
  }

  def 'should ignore hashes if hashes disabled'() {
    setup:
    def data = createData('blerp', [a: 'b'])

    when: //initial write
    ((WriteableCache) cache).merge('foo', data)

    then:
    1 * cacheMetrics.merge('test', 'foo', 1, 1, 0, 0, 1, 1, 1, 1, 1, 0,)

    when: //second write, hash matches
    ((WriteableCache) cache).merge('foo', data)

    then:
    1 * cacheMetrics.merge('test', 'foo', 1, 0, 0, 1, 0, 0, 0, 0, 0, 0)

    when: //third write, disable hashing
    pool.resource.withCloseable { Jedis j -> j.set('test:foo:hashes.disabled', 'true') }
    ((WriteableCache) cache).merge('foo', data)

    then:
    1 * cacheMetrics.merge('test', 'foo', 1, 1, 0, 0, 1, 1, 1, 1, 1, 0)
  }

  def 'should not write an item if it is unchanged'() {
    setup:
    def data = createData('blerp', [a: 'b'])

    when:
    ((WriteableCache) cache).merge('foo', data)

    then:
    1 * cacheMetrics.merge('test', 'foo', 1, 1, 0, 0, 1, 1, 1, 1, 1, 0)

    when:
    ((WriteableCache) cache).merge('foo', data)

    then:
    1 * cacheMetrics.merge('test', 'foo', 1, 0, 0, 1, 0, 0, 0, 0, 0, 0)
  }

  def 'should merge #mergeCount items at a time'() {
    setup:
    def cache = new RedisCache(
      'test',
      new JedisClientDelegate(pool),
      new ObjectMapper(),
      RedisCacheOptions.builder().maxMergeBatch(mergeCount).maxMset(MAX_MSET_SIZE).hashing(false).build(),
      cacheMetrics)

    when:
    cache.mergeAll('foo', items)

    then:

    fullMerges * cacheMetrics.merge('test', 'foo', mergeCount, mergeCount, 0, 0, 0, 1, mergeCount, 0, 1, 0)
    finalMergeCount * cacheMetrics.merge('test', 'foo', finalMerge, finalMerge, 0, 0, 0, 1, finalMerge, 0, 1, 0)

    where:
    mergeCount << [1, 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 100, 101, 131]
    items = (0..100).collect { createData("blerp-$it") }
    fullMerges = items.size() / mergeCount
    finalMerge = items.size() % mergeCount
    finalMergeCount = finalMerge > 0 ? 1 : 0
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
