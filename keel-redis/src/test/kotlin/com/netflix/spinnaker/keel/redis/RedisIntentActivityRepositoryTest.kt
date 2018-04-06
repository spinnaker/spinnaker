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
import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.config.configureObjectMapper
import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.keel.*
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.model.PagingListCriteria
import com.netflix.spinnaker.keel.test.GenericTestIntentSpec
import com.netflix.spinnaker.keel.test.TestIntent
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import redis.clients.jedis.JedisPool

@TestInstance(Lifecycle.PER_CLASS)
object RedisIntentActivityRepositoryTest {

  val embeddedRedis = EmbeddedRedis.embed()
  val jedisPool = embeddedRedis.pool as JedisPool
  val keelProperties = KeelProperties().apply {
    maxConvergenceLogEntriesPerIntent = 5
  }
  val mapper = configureObjectMapper(ObjectMapper(), keelProperties, listOf(
    ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(Intent::class.java, listOf("com.netflix.spinnaker.keel"))
  ))

  val subject = RedisIntentActivityRepository(
    redisClientSelector = RedisClientSelector(listOf(JedisClientDelegate("primaryDefault", jedisPool))),
    keelProperties = keelProperties,
    objectMapper = mapper
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
  fun `listing log for an intent returns ordered records`() {
    val aUpsertRecord = IntentChangeRecord("a", "rob", IntentChangeAction.UPSERT, TestIntent(GenericTestIntentSpec("a")))
    val aConvergeRecord = IntentConvergenceRecord("a", "keel", ConvergeResult(listOf(), ChangeSummary("a")))
    val bConvergeRecord = IntentConvergenceRecord("b", "keel", ConvergeResult(listOf(), ChangeSummary("b")))
    val aUpsertRecord2 = IntentChangeRecord("a", "rob", IntentChangeAction.UPSERT, TestIntent(GenericTestIntentSpec("a")))
    subject.record(aUpsertRecord)
    subject.record(aConvergeRecord)
    subject.record(bConvergeRecord)
    subject.record(aUpsertRecord2)

    subject.getHistory("a", PagingListCriteria()).size shouldMatch equalTo(3)
    subject.getHistory("a", IntentChangeRecord::class.java, PagingListCriteria()).size shouldMatch equalTo(2)
  }

  @Test
  fun `only the specified number of convergence log messages should be kept`() {
    val intentId = "Application:emilykeeltest"

    val recordFactory = {
      IntentConvergenceRecord(
        intentId = intentId,
        actor = "keel:scheduledConvergence",
        result = ConvergeResult(listOf(), ChangeSummary("a"))
      )
    }
    subject.record(recordFactory())
    subject.record(recordFactory())
    subject.record(recordFactory())
    subject.record(recordFactory())
    subject.record(recordFactory())
    subject.record(recordFactory())
    subject.record(recordFactory())

    subject.getHistory(intentId, PagingListCriteria()).size shouldEqual keelProperties.maxConvergenceLogEntriesPerIntent
  }
}
