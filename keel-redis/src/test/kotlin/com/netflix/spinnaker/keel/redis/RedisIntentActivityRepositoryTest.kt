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

import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import redis.clients.jedis.JedisPool
import java.time.Clock

@TestInstance(Lifecycle.PER_CLASS)
object RedisIntentActivityRepositoryTest {

  val embeddedRedis = EmbeddedRedis.embed()
  val jedisPool = embeddedRedis.pool as JedisPool
  val clock = Clock.systemDefaultZone()
  val subject = RedisIntentActivityRepository(JedisClientDelegate(jedisPool), null, clock)

  @BeforeEach
  fun setup() {
    jedisPool.resource.use {
      it.flushDB()
    }
  }

  @AfterAll
  fun cleanup() {
    embeddedRedis.destroy()
  }

  @Test
  fun `listing history for an intent returns ordered orchestrations`() {
    subject.addOrchestration("hello", "abcd")
    subject.addOrchestrations("world", listOf("1234", "5678", "lol"))
    subject.addOrchestration("hello", "covfefe")

    subject.getHistory("world").let {
      it shouldMatch equalTo(listOf("1234", "5678", "lol"))
    }

    subject.getHistory("hello").let {
      it shouldMatch equalTo(listOf("abcd", "covfefe"))
    }
  }
}
