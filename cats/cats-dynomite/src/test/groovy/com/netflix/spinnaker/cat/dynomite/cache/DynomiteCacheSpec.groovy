/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.cat.dynomite.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.dyno.connectionpool.ConnectionPoolConfiguration.LoadBalancingStrategy
import com.netflix.dyno.connectionpool.Host
import com.netflix.dyno.connectionpool.Host.Status
import com.netflix.dyno.connectionpool.HostSupplier
import com.netflix.dyno.connectionpool.TokenMapSupplier
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl
import com.netflix.dyno.connectionpool.impl.lb.HostToken
import com.netflix.dyno.jedis.DynoJedisClient
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.cache.WriteableCacheSpec
import com.netflix.spinnaker.cats.dynomite.DynomiteClientDelegate
import com.netflix.spinnaker.cats.dynomite.cache.DynomiteCache
import com.netflix.spinnaker.cats.redis.cache.AbstractRedisCache.CacheMetrics
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import redis.clients.jedis.Jedis
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Unroll

// TODO rz - Figure out how to get dyno client to connect w/ an embedded redis
@IgnoreIf({ System.getProperty("dyno.address") == null })
class DynomiteCacheSpec extends WriteableCacheSpec {

  static int MAX_MSET_SIZE = 2
  static int MAX_MERGE_COUNT = 1

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  DynoJedisClient client

  Jedis directClient

  CacheMetrics cacheMetrics = Mock(CacheMetrics)

  @Override
  Cache getSubject() {
//    if (System.getProperty('dyno.hostname') == null) {
//      initEmbeddedRedisClient()
//    } else {
      initLocalDynoClusterClient()
//    }

    def delegate = new DynomiteClientDelegate(client)

    return new DynomiteCache(
      'test',
      delegate,
      new ObjectMapper(),
      RedisCacheOptions.builder().maxMset(MAX_MSET_SIZE).maxMergeBatch(MAX_MERGE_COUNT).build(),
      cacheMetrics
    )
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

  def 'should not write an item if it is unchanged'() {
    setup:
    def data = createData('blerp', [a: 'b'])

    when:
    ((WriteableCache) cache).merge('foo', data)

    then:
    1 * cacheMetrics.merge('test', 'foo', 1, 1, 0, 0, 1, 2, 1, 0, 1, 0, 0)

    when:
    ((WriteableCache) cache).merge('foo', data)

    then:
    1 * cacheMetrics.merge('test', 'foo', 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0)
  }

  private static class Bean {
    String key
    Integer key2

    Bean(String key, Integer key2) {
      this.key = key
      this.key2 = key2
    }
  }

  private void initLocalDynoClusterClient() {
    directClient = new Jedis('192.168.99.100', 22122)
    directClient.flushAll()
    directClient.close()

    // This setup assumes that you're running a single-node Dynomite cluster via the Docker image.
    def localHost = new Host(Optional.ofNullable(System.getProperty('dyno.address')).orElse('192.168.99.100'), 8102, 'localrack', Status.Up)

    def localHostSupplier = new HostSupplier() {
      @Override
      Collection<Host> getHosts() {
        return [localHost]
      }
    }

    def tokenSupplier = new TokenMapSupplier() {
      final HostToken localHostToken = new HostToken(437425602L, localHost)
      @Override
      List<HostToken> getTokens(Set<Host> activeHosts) {
        return [localHostToken]
      }

      @Override
      HostToken getTokenForHost(Host host, Set<Host> activeHosts) {
        return localHostToken
      }
    }

    client = new DynoJedisClient.Builder()
      .withApplicationName('catsTest')
      .withDynomiteClusterName('dyn_o_mite')
      .withHostSupplier(localHostSupplier)
      .withCPConfig(
      new ConnectionPoolConfigurationImpl('catsTest')
        .withTokenSupplier(tokenSupplier)
        .setLocalRack('localrack')
        .setLocalDataCenter('localrac')
        .setPoolShutdownDelay(2)
    )
      .build()

  }

  private void initEmbeddedRedisClient() {
    if (!embeddedRedis) {
      embeddedRedis = EmbeddedRedis.embed()
    }
    Jedis jedis = embeddedRedis.jedis
    try {
      jedis.flushAll()
    } finally {
      jedis?.close()
    }

    def localHost = new Host('localhost', embeddedRedis.port, 'localrack', Status.Up)

    def localHostSupplier = new HostSupplier() {
      @Override
      Collection<Host> getHosts() {
        return [localHost]
      }
    }

    def tokenSupplier = new TokenMapSupplier() {
      final HostToken localHostToken = new HostToken(437425602L, localHost)
      @Override
      List<HostToken> getTokens(Set<Host> activeHosts) {
        return [localHostToken]
      }

      @Override
      HostToken getTokenForHost(Host host, Set<Host> activeHosts) {
        return localHostToken
      }
    }

    client = new DynoJedisClient.Builder()
      .withApplicationName('catsTest')
      .withDynomiteClusterName('dyn_o_mite')
      .withHostSupplier(localHostSupplier)
      .withCPConfig(
      new ConnectionPoolConfigurationImpl('catsTest')
        .setLoadBalancingStrategy(LoadBalancingStrategy.RoundRobin)
        .withTokenSupplier(tokenSupplier)
        .setLocalRack('localrack')
        .setLocalDataCenter('localrac')
    )
      .build()
  }
}

