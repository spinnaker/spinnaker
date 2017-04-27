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

package com.netflix.spinnaker.orca.q.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.ScheduledAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.util.Pool
import java.io.Closeable
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import java.time.temporal.TemporalAmount
import java.util.*
import java.util.UUID.randomUUID
import javax.annotation.PreDestroy

class RedisQueue(
  queueName: String,
  private val pool: Pool<Jedis>,
  private val clock: Clock,
  private val currentInstanceId: String,
  private val lockTtlSeconds: Int = Duration.ofDays(1).seconds.toInt(),
  override val ackTimeout: TemporalAmount = Duration.ofMinutes(1)
) : Queue, Closeable {

  private val mapper = ObjectMapper().apply {
    registerModule(KotlinModule())
  }
  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val queueKey = queueName + ".queue"
  private val unackedKey = queueName + ".unacked"
  private val messagesKey = queueName + ".messages"
  private val locksKey = queueName + ".locks"

  private val redeliveryWatcher = ScheduledAction(this::redeliver)

  override fun poll(callback: (Message, () -> Unit) -> Unit) {
    pool.resource.use { redis ->
      redis
        .pop(queueKey, unackedKey, ackTimeout)
        ?.let { id -> Pair(UUID.fromString(id), redis.hget(messagesKey, id)) }
        ?.let { (id, json) -> Pair(id, mapper.readValue<Message>(json)) }
        ?.let { (id, payload) ->
          callback.invoke(payload) {
            ack(id)
          }
        }
    }
  }

  override fun push(message: Message, delay: TemporalAmount) {
    pool.resource.use { redis ->
      val id = randomUUID().toString()
      redis.hset(messagesKey, id, mapper.writeValueAsString(message))
      redis.zadd(queueKey, score(delay), id)
    }
  }

  @PreDestroy override fun close() {
    log.info("stopping redelivery watcher for $this")
    redeliveryWatcher.close()
  }

  private fun ack(id: UUID) {
    pool.resource.use { redis ->
      redis.zrem(unackedKey, id.toString())
      redis.hdel(messagesKey, id.toString())
    }
  }

  internal fun redeliver() {
    pool.resource.use { redis ->
      redis.popAll(unackedKey, queueKey).apply {
        if (size > 0) log.warn("Redelivering $size messages")
      }
    }
  }

  /**
   * Attempt to acquire a lock on a single value from a sorted set [from], adding
   * them to sorted set [to] (with optional [delay]).
   */
  private fun JedisCommands.pop(from: String, to: String, delay: TemporalAmount = ZERO) =
    zrangeByScore(from, 0.0, score(), 0, 1)
      .takeIf {
        getSet("$locksKey:$it", currentInstanceId) in listOf(null, currentInstanceId)
      }
      ?.also {
        expire("$locksKey:$it", lockTtlSeconds)
        move(from, to, delay, it)
      }
      ?.firstOrNull()

  /**
   * Pop values from sorted set [from] and add them to sorted set [to] (with
   * optional [delay]).
   *
   * @return the popped values.
   */
  private fun JedisCommands.popAll(from: String, to: String, delay: TemporalAmount = ZERO) =
    zrangeByScore(from, 0.0, score())
      .also { it.forEach { del("$locksKey:$it") } }
      .also { move(from, to, delay, it) }

  /**
   * Move [values] from sorted set [from] to sorted set [to]
   */
  private fun JedisCommands.move(from: String, to: String, delay: TemporalAmount, values: Set<String>) {
    if (values.isNotEmpty()) {
      zrem(from, *values.toTypedArray())
      val score = score(delay)
      zadd(to, values.associate { Pair(it, score) })
    }
  }

  /**
   * @return current time (plus optional [delay]) converted to a score for a
   * Redis sorted set.
   */
  private fun score(delay: TemporalAmount = ZERO) =
    clock.instant().plus(delay).toEpochMilli().toDouble()

  inline fun <reified R> ObjectMapper.readValue(content: String): R =
    readValue(content, R::class.java)
}
