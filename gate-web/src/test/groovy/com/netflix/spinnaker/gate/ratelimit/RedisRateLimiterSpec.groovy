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
package com.netflix.spinnaker.gate.ratelimit

import com.netflix.spinnaker.gate.redis.EmbeddedRedis
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RedisRateLimiterSpec extends Specification {

  static int port

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    embeddedRedis.jedis.flushDB()
    port = embeddedRedis.port
  }

  def cleanup() {
    embeddedRedis.jedis.flushDB()
  }

  def 'should create new bucket for unknown principal'() {
    given:
    RedisRateLimiter subject = new RedisRateLimiter((JedisPool) embeddedRedis.pool, 10, 10, [:])

    when:
    Rate rate = subject.incrementAndGetRate('user@example.com')

    then:
    noExceptionThrown()
    rate.capacity == 10
    rate.remaining == 9
    rate.reset > new Date().getTime()
    !rate.isThrottled()
  }

  def 'should fill bucket after expiry'() {
    given:
    RedisRateLimiter subject = new RedisRateLimiter((JedisPool) embeddedRedis.pool, 10, 1, [:])

    when:
    Rate rate = subject.incrementAndGetRate('user@example.com')

    then:
    noExceptionThrown()
    rate.capacity == 10
    rate.remaining == 9

    when:
    rate = subject.incrementAndGetRate('user@example.com')

    then:
    rate.capacity == 10
    rate.remaining == 8

    when:
    sleep(1000)
    rate = subject.incrementAndGetRate('user@example.com')

    then:
    rate.remaining == 9
  }

  def 'should throttle after bucket empties'() {
    given:
    RedisRateLimiter subject = new RedisRateLimiter((JedisPool) embeddedRedis.pool, 3, 1, [:])

    when:
    subject.incrementAndGetRate('user@example.com')
    Rate rate = subject.incrementAndGetRate('user@example.com')

    then:
    rate.remaining == 1
    !rate.throttled

    when:
    rate = subject.incrementAndGetRate('user@example.com')

    then:
    rate.remaining == 0
    !rate.throttled

    when:
    rate = subject.incrementAndGetRate('user@example.com')

    then:
    rate.remaining == 0
    rate.throttled
  }

  def 'should allow static and dynamic capacity overrides'() {
    given:
    RedisRateLimiter subject = new RedisRateLimiter((JedisPool) embeddedRedis.pool, 3, 1, [
      'foo': 5
    ])

    Jedis jedis = embeddedRedis.pool.resource
    jedis.set("rateLimit:capacity:bar", "8")
    jedis.set("rateLimit:capacity:invalid", "hello")

    expect:
    subject.getCapacity(jedis, 'no-override') == 3
    subject.getCapacity(jedis, 'foo') == 5
    subject.getCapacity(jedis, 'bar') == 8
    subject.getCapacity(jedis, 'invalid') == 3

    cleanup:
    jedis.close()
  }

  def 'should use same capacity override for all anonymous principals'() {
    given:
    RedisRateLimiter subject = new RedisRateLimiter((JedisPool) embeddedRedis.pool, 3, 1, [
      'anonymous': 5
    ])

    Jedis jedis = embeddedRedis.pool.resource

    expect:
    subject.getCapacity(jedis, 'foo') == 3
    subject.getCapacity(jedis, 'anonymous') == 5
    subject.getCapacity(jedis, 'anonymous-10.10.10.10') == 5

    cleanup:
    jedis.close()
  }
}
