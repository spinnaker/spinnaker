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
package com.netflix.spinnaker.orca.q.redis

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.q.CompleteExecution
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.spek.and
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

object RedisDeadMessageHandlerSpec : SubjectSpek<RedisDeadMessageHandler>({

  val queue: Queue = mock()
  val redis: Jedis = mock()
  val redisPool: Pool<Jedis> = mock()
  val clock = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())

  subject(CachingMode.GROUP) {
    whenever(redisPool.resource).thenReturn(redis)
    RedisDeadMessageHandler("dlq", redisPool, clock)
  }

  fun resetMocks() = reset(queue, redis)

  describe("handling a message") {
    val message = StartExecution(Pipeline::class.java, "1", "spinnaker")

    afterGroup(::resetMocks)

    action("the handler receives a message") {
      subject.handle(queue, message)
    }

    it("terminates the execution") {
      verify(queue).push(CompleteExecution(message))
    }

    it("puts the message onto the DLQ") {
      verify(redis).zadd("dlq.messages", 0.0, "{\"@class\":\".StartExecution\",\"executionType\":\"com.netflix.spinnaker.orca.pipeline.model.Pipeline\",\"executionId\":\"1\",\"application\":\"spinnaker\",\"attributes\":[]}")
    }
  }
})
