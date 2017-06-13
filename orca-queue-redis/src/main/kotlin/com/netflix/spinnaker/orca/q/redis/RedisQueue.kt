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
import com.google.common.hash.Hashing
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.metrics.*
import org.funktionale.partials.partially1
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.jedis.Transaction
import redis.clients.util.Pool
import java.io.IOException
import java.nio.charset.Charset
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import java.time.temporal.TemporalAmount
import java.util.UUID.randomUUID

class RedisQueue(
  private val queueName: String,
  private val pool: Pool<Jedis>,
  private val clock: Clock,
  private val lockTtlSeconds: Int = 10,
  override val ackTimeout: TemporalAmount = Duration.ofMinutes(1),
  override val deadMessageHandler: (Queue, Message) -> Unit,
  override val publisher: ApplicationEventPublisher
) : MonitorableQueue {

  private val mapper = ObjectMapper().apply {
    registerModule(KotlinModule())
  }
  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val queueKey = "$queueName.queue"
  private val unackedKey = "$queueName.unacked"
  private val messagesKey = "$queueName.messages"
  private val attemptsKey = "$queueName.attempts"
  private val locksKey = "$queueName.locks"
  private val hashKey = "$queueName.hash"
  private val hashesKey = "$queueName.hashes"

  override fun poll(callback: (Message, () -> Unit) -> Unit) {
    pool.resource.use { redis ->
      redis.zrangeByScore(queueKey, 0.0, score(), 0, 1)
        .firstOrNull()
        ?.takeIf { id -> redis.acquireLock(id) }
        ?.also { id ->
          val ack = this::ackMessage.partially1(id)
          redis.readMessage(id) { message ->
            callback(message, ack)
          }
        }
      fire<QueuePolled>()
    }
  }

  override fun push(message: Message, delay: TemporalAmount) {
    pool.resource.use { redis ->
      val messageHash = message.hash()
      if (redis.sismember(hashesKey, messageHash)) {
        log.warn("Ignoring message as an identical one is already on the queue: $message")
        fire<MessageDuplicate>()
      } else {
        redis.queueMessage(message, delay)
        fire<MessagePushed>()
      }
    }
  }

  @Scheduled(fixedDelayString = "\${queue.retry.frequency.ms:10000}")
  override fun retry() {
    pool.resource.use { redis ->
      redis
        .zrangeByScore(unackedKey, 0.0, score())
        .let { ids ->
          if (ids.size > 0) {
            ids
              .map { "$locksKey:$it" }
              .let { redis.del(*it.toTypedArray()) }
          }

          ids.forEach { id ->
            val attempts = redis.hgetInt(attemptsKey, id)
            if (attempts >= Queue.maxRetries) {
              redis.readMessage(id) { message ->
                log.warn("Message $id with payload $message exceeded max retries")
                handleDeadMessage(message)
                redis.removeMessage(id)
              }
              fire<MessageDead>()
            } else {
              if (redis.sismember(hashesKey, redis.hget(hashKey, id))) {
                log.warn("Not retrying message $id because an identical message is already on the queue")
                redis.removeMessage(id)
                fire<MessageDuplicate>()
              } else {
                log.warn("Retrying message $id after $attempts attempts")
                redis.requeueMessage(id)
                fire<MessageRetried>()
              }
            }
          }
        }
        .also {
          fire<RetryPolled>()
        }
    }
  }

  override fun readState(): QueueState =
    pool.resource.use { redis ->
      redis.multi {
        zcard(queueKey)
        zcount(queueKey, 0.0, score())
        zcard(unackedKey)
        hlen(messagesKey)
      }
        .map { (it as Long).toInt() }
        .let { (queued, ready, processing, messages) ->
          return QueueState(
            depth = queued,
            ready = ready,
            unacked = processing,
            orphaned = messages - (queued + processing)
          )
        }
    }

  override fun toString() = "RedisQueue[$queueName]"

  private fun ackMessage(id: String) {
    pool.resource.use { redis ->
      redis.removeMessage(id)
      fire<MessageAcknowledged>()
    }
  }

  private fun Jedis.queueMessage(message: Message, delay: TemporalAmount = ZERO) {
    val id = randomUUID().toString()
    val hash = message.hash()
    multi {
      hset(messagesKey, id, mapper.writeValueAsString(message))
      zadd(queueKey, score(delay), id)
      hset(hashKey, id, hash)
      sadd(hashesKey, hash)
    }
  }

  private fun Jedis.requeueMessage(id: String) {
    val hash = hget(hashKey, id)
    multi {
      zrem(unackedKey, id)
      zadd(queueKey, score(), id)
      if (hash != null) {
        sadd(hashesKey, hash)
      }
    }
  }

  private fun Jedis.removeMessage(id: String) {
    multi {
      zrem(queueKey, id)
      zrem(unackedKey, id)
      hdel(messagesKey, id)
      hdel(attemptsKey, id)
      hdel(hashKey, id)
    }
  }

  /**
   * Tries to read the message with the specified [id] passing it to [block].
   * If it's not accessible for whatever reason any references are cleaned up.
   */
  private fun Jedis.readMessage(id: String, block: (Message) -> Unit) {
    val hash = hget(hashKey, id)
    multi {
      hget(messagesKey, id)
      zrem(queueKey, id)
      zadd(unackedKey, score(ackTimeout), id)
      if (hash != null) {
        srem(hashesKey, hash)
      }
      hincrBy(attemptsKey, id, 1)
    }.let {
      val json = it[0] as String?
      if (json == null) {
        log.error("Payload for message $id is missing")
        // clean up what is essentially an unrecoverable message
        removeMessage(id)
      } else {
        try {
          val message = mapper.readValue<Message>(json)
          block.invoke(message)
        } catch(e: IOException) {
          log.error("Failed to read message $id", e)
          removeMessage(id)
        }
      }
    }
  }

  private fun handleDeadMessage(it: Message) {
    deadMessageHandler.invoke(this, it)
  }

  private fun Jedis.acquireLock(id: String) =
    (set("$locksKey:$id", "\uD83D\uDD12", "NX", "EX", lockTtlSeconds) == "OK")
      .also {
        if (!it) {
          fire<LockFailed>()
        }
      }

  /**
   * @return current time (plus optional [delay]) converted to a score for a
   * Redis sorted set.
   */
  private fun score(delay: TemporalAmount = ZERO) =
    clock.instant().plus(delay).toEpochMilli().toDouble()

  private inline fun <reified R> ObjectMapper.readValue(content: String): R =
    readValue(content, R::class.java)

  private fun Jedis.multi(block: Transaction.() -> Unit) =
    multi().use { tx ->
      tx.block()
      tx.exec()
    }

  private fun JedisCommands.hgetInt(key: String, field: String, default: Int = 0) =
    hget(key, field)?.toInt() ?: default

  private fun Message.hash() =
    Hashing
      .murmur3_32()
      .hashString(toString(), Charset.defaultCharset())
      .toString()
}
