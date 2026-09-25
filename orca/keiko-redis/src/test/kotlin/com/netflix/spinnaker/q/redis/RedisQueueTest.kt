/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.q.redis

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.q.AttemptsAttribute
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.MaxAttemptsAttribute
import com.netflix.spinnaker.q.QueueTest
import com.netflix.spinnaker.q.TestMessage
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.MonitorableQueueTest
import com.netflix.spinnaker.q.metrics.QueueEvent
import java.time.Clock
import java.util.Optional

object RedisQueueTest : QueueTest<RedisQueue>(createQueueNoPublisher, ::shutdownCallback)

object RedisMonitorableQueueTest : MonitorableQueueTest<RedisQueue>(
  ::createQueue,
  RedisQueue::retry,
  ::shutdownCallback
)

private var redis: EmbeddedRedis? = null

private val createQueueNoPublisher = { clock: Clock,
  deadLetterCallback: DeadMessageCallback ->
  createQueue(clock, deadLetterCallback, null)
}

private fun createQueue(clock: Clock,
                        deadLetterCallback: DeadMessageCallback,
                        publisher: EventPublisher?): RedisQueue {
  redis = EmbeddedRedis.embed()
  return RedisQueue(
    queueName = "test",
    pool = redis!!.pool,
    clock = clock,
    deadMessageHandlers = listOf(deadLetterCallback),
    publisher = publisher ?: (
      object : EventPublisher {
        override fun publishEvent(event: QueueEvent) {}
      }
      ),
     mapper = JsonMapper.builder()
       .addModule(KotlinModule.Builder().build())
       .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
       // Jackson 3 default changed: merge into getter-only collections (message attributes).
       .enable(tools.jackson.databind.MapperFeature.USE_GETTERS_AS_SETTERS)
       .registerSubtypes(TestMessage::class.java)
       .registerSubtypes(MaxAttemptsAttribute::class.java, AttemptsAttribute::class.java)
       .build(),
    serializationMigrator = Optional.empty()
  )
}

private fun shutdownCallback() {
  redis?.destroy()
}
