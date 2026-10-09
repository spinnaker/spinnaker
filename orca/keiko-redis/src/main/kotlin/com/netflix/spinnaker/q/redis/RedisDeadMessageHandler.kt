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
package com.netflix.spinnaker.q.redis

import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import java.time.Clock
import redis.clients.jedis.Jedis
import redis.clients.jedis.util.Pool
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * A dead message handler that writes messages to a sorted set with a score
 * representing the time the message was abandoned.
 */
class RedisDeadMessageHandler(
  deadLetterQueueName: String,
  private val pool: Pool<Jedis>,
  private val clock: Clock
) : DeadMessageCallback {

  private val dlqKey = "$deadLetterQueueName.messages"

  private val mapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .build()

  override fun invoke(queue: Queue, message: Message) {
    pool.resource.use { redis ->
      val score = clock.instant().toEpochMilli().toDouble()
      redis.zadd(dlqKey, score, mapper.writeValueAsString(message))
    }
  }
}
