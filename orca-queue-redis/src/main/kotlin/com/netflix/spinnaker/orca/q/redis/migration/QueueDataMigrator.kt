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

package com.netflix.spinnaker.orca.q.redis.migration

import com.netflix.spinnaker.config.RedisQueueProperties
import redis.clients.jedis.Jedis
import redis.clients.jedis.Tuple
import redis.clients.util.Pool
import javax.annotation.PostConstruct

class QueueDataMigrator(
  private val redisPool: Pool<Jedis>,
  private val properties: RedisQueueProperties
) {
  private val messagesKey = "${properties.queueName}.messages"
  private val dlqKey = "${properties.deadLetterQueueName}.messages"
  private val regex = "com\\.netflix\\.spinnaker\\.orca\\.pipeline\\.model\\.(Pipeline|Orchestration)".toRegex()

  @PostConstruct
  fun migrateExecutionType() {
    redisPool.resource.use { redis ->
      redis
        .hgetAll(messagesKey)
        .filter { (_, message) -> message.contains(regex) }
        .forEach { hash, message ->
          redis.hset(messagesKey, hash, message.update())
        }
      redis
        .zrangeByScoreWithScores(dlqKey, "-inf", "+inf")
        .filter { (message, _) -> message.contains(regex) }
        .forEach { (message, score) ->
          redis.multi().use { tx ->
            tx.zrem(dlqKey, message)
            tx.zadd(dlqKey, score, message.update())
            tx.exec()
          }
        }
    }
  }

  private fun String.update() =
    replace(regex) { match ->
      match.destructured.component1().toUpperCase()
    }

  private operator fun Tuple.component1() = element
  private operator fun Tuple.component2() = score
}
