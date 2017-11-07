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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.keel.test.TestIntent
import com.netflix.spinnaker.keel.test.TestIntentSpec
import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import redis.clients.jedis.JedisPool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Clock

class RedisTraceRepositorySpec extends Specification {

  @Shared
  RedisTraceRepository traceRepository

  @Shared
  JedisPool jedisPool

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  @Shared
  ObjectMapper objectMapper = new ObjectMapper().with {
    registerSubtypes(TestIntent)
    registerModule(new KotlinModule())
  }

  @Shared
  Clock clock = Clock.systemDefaultZone()

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
    jedisPool = embeddedRedis.pool as JedisPool
    traceRepository = new RedisTraceRepository(new JedisClientDelegate(jedisPool), null, objectMapper, clock)
  }

  def setup() {
    jedisPool.resource.withCloseable {
      it.flushDB()
    }
  }

  def "listing traces for an intent returns ordered traces"() {
    given:
    traceRepository.record(new Trace([:], new TestIntent(new TestIntentSpec("1", [placement: 1]), []), null))
    traceRepository.record(new Trace([:], new TestIntent(new TestIntentSpec("1", [placement: 2]), []), null))
    traceRepository.record(new Trace([:], new TestIntent(new TestIntentSpec("2", [placement: 3]), []), null))

    when:
    def result = traceRepository.getForIntent("test:1")

    then:
    result.size() == 2
    result.intent*.spec.data['placement'] == [1, 2]
  }
}
