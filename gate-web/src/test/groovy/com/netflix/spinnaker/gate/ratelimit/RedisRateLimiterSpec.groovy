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
    RedisRateLimiter subject = new RedisRateLimiter((JedisPool) embeddedRedis.pool)

    and:
    RateLimitPrincipal principal = new RateLimitPrincipal('user@example.com', 10, 10, true)

    when:
    Rate rate = subject.incrementAndGetRate(principal)

    then:
    noExceptionThrown()
    rate.capacity == 10
    rate.remaining == 9
    rate.reset > new Date().getTime()
    !rate.isThrottled()
  }

  def 'should fill bucket after expiry'() {
    given:
    RedisRateLimiter subject = new RedisRateLimiter((JedisPool) embeddedRedis.pool)

    and:
    RateLimitPrincipal principal = new RateLimitPrincipal('user@example.com', 1, 10, true)

    when:
    Rate rate = subject.incrementAndGetRate(principal)

    then:
    noExceptionThrown()
    rate.capacity == 10
    rate.remaining == 9

    when:
    rate = subject.incrementAndGetRate(principal)

    then:
    rate.capacity == 10
    rate.remaining == 8

    when:
    sleep(1000)
    rate = subject.incrementAndGetRate(principal)

    then:
    rate.remaining == 9
  }

  def 'should throttle after bucket empties'() {
    given:
    RedisRateLimiter subject = new RedisRateLimiter((JedisPool) embeddedRedis.pool)

    and:
    RateLimitPrincipal principal = new RateLimitPrincipal('user@example.com', 1, 3, true)

    when:
    subject.incrementAndGetRate(principal)
    Rate rate = subject.incrementAndGetRate(principal)

    then:
    rate.remaining == 1
    !rate.throttled

    when:
    rate = subject.incrementAndGetRate(principal)

    then:
    rate.remaining == 0
    !rate.throttled

    when:
    rate = subject.incrementAndGetRate(principal)

    then:
    rate.remaining == 0
    rate.throttled
  }
}
