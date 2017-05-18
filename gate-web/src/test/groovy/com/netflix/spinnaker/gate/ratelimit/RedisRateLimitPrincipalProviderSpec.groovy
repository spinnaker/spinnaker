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

import com.netflix.spinnaker.gate.config.RateLimiterConfiguration
import com.netflix.spinnaker.gate.config.RateLimiterConfiguration.PrincipalOverride
import com.netflix.spinnaker.gate.redis.EmbeddedRedis
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class RedisRateLimitPrincipalProviderSpec extends Specification {

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

  @Unroll("#principalName should have capacity=#expectedCapacity, rateSeconds=#expectedRateSeconds, learning=#expectedLearning")
  def 'should allow static and dynamic overrides'() {
    given:
    RedisRateLimitPrincipalProvider subject = new RedisRateLimitPrincipalProvider(
      (JedisPool) embeddedRedis.pool,
      new RateLimiterConfiguration(
        capacity: 60,
        capacityByPrincipal: [
          new PrincipalOverride(principal: 'anonymous', override: 5),
          new PrincipalOverride(principal: 'static-override', override: 20)
        ],
        rateSecondsByPrincipal: [
          new PrincipalOverride(principal: 'anonymous', override: 5),
          new PrincipalOverride(principal: 'static-override', override: 20)
        ],
        learning: true
      )
    )

    and:
    Jedis jedis
    try {
      jedis = embeddedRedis.pool.resource
      jedis.sadd("rateLimit:enforcing", 'redis-enforced', 'redis-conflict')
      jedis.sadd("rateLimit:ignoring", 'redis-ignored', 'redis-conflict')
      jedis.set('rateLimit:capacity:redis-override', '15')
      jedis.set('rateLimit:rateSeconds:redis-override', '15')
    } finally {
      jedis?.close()
    }

    when:
    def principal = subject.getPrincipal(principalName)

    then:
    principal.capacity == expectedCapacity
    principal.rateSeconds == expectedRateSeconds
    principal.learning == expectedLearning

    where:
    principalName           || expectedCapacity | expectedRateSeconds | expectedLearning
    'anonymous-10.10.10.10' || 5                | 5                   | true
    'redis-enforced'        || 60               | 10                  | false
    'redis-ignored'         || 60               | 10                  | true
    'redis-conflict'        || 60               | 10                  | false
    'static-override'       || 20               | 20                  | true
    'redis-override'        || 15               | 15                  | true
  }
}
