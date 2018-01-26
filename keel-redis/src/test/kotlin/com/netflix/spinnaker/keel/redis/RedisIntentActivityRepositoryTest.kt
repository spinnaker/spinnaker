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
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.config.KeelConfiguration
import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.keel.IntentConvergenceRecord
import com.netflix.spinnaker.keel.dryrun.ChangeType
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
  val keelProperties = KeelProperties().apply {
    maxConvergenceLogEntriesPerIntent = 5
  }
  val clock = Clock.systemDefaultZone()
  val mapper = KeelConfiguration()
    .apply { properties = keelProperties }
    .objectMapper(ObjectMapper(), listOf())

  val subject = RedisIntentActivityRepository(
    mainRedisClientDelegate = JedisClientDelegate(jedisPool),
    previousRedisClientDelegate = null,
    keelProperties = keelProperties ,
    objectMapper = mapper,
    clock = clock
  )

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

  @Test
  fun `only the specified number of convergence log messages should be kept`() {
    val intentId = "Application:emilykeeltest"

    val record = IntentConvergenceRecord(
      intentId = intentId,
      changeType = ChangeType.NO_CHANGE,
      orchestrations = emptyList(),
      messages = listOf("System state matches desired state"),
      diff = emptySet(),
      actor = "keel:scheduledConvergence",
      timestampMillis = 1516214128706
    )
    subject.logConvergence(record)
    subject.logConvergence(record.copy(timestampMillis = 1516214135581))
    subject.logConvergence(record.copy(timestampMillis = 1516214157620))
    subject.logConvergence(record.copy(timestampMillis = 1516214167665))
    subject.logConvergence(record.copy(timestampMillis = 1516214192806))
    subject.logConvergence(record.copy(timestampMillis = 1516214217947))
    subject.logConvergence(record.copy(timestampMillis = 1516214243088))

    subject.getLog(intentId).size shouldEqual keelProperties.maxConvergenceLogEntriesPerIntent
  }
}
