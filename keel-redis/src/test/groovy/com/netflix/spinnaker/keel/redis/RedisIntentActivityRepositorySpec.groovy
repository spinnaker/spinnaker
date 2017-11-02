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
package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import redis.clients.jedis.JedisPool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Clock

class RedisIntentActivityRepositorySpec extends Specification {

  @Shared
  RedisIntentActivityRepository subject

  @Shared
  JedisPool jedisPool

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Shared
  Clock clock = Clock.systemDefaultZone()

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    jedisPool = embeddedRedis.pool as JedisPool
    subject = new RedisIntentActivityRepository(new JedisClientDelegate(jedisPool), null, clock)
  }

  def setup() {
    jedisPool.resource.withCloseable {
      it.flushDB()
    }
  }

  def "listing history for an intent returns ordered orchestrations"() {
    given:
    subject.addOrchestration("hello", "abcd")
    subject.addOrchestrations("world", ["1234", "5678", "lol"])
    subject.addOrchestration("hello", "covfefe")

    when:
    def result = subject.getHistory("world")

    then:
    result == ["1234", "5678", "lol"]

    when:
    result = subject.getHistory("hello")

    then:
    result == ["abcd", "covfefe"]
  }
}
