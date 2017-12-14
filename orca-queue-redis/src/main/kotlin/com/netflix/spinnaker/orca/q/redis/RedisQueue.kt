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

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.common.hash.Hashing
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import com.netflix.spinnaker.orca.q.AttemptsAttribute
import com.netflix.spinnaker.orca.q.MaxAttemptsAttribute
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.metrics.LockFailed
import com.netflix.spinnaker.orca.q.metrics.MessageAcknowledged
import com.netflix.spinnaker.orca.q.metrics.MessageDead
import com.netflix.spinnaker.orca.q.metrics.MessageDuplicate
import com.netflix.spinnaker.orca.q.metrics.MessageNotFound
import com.netflix.spinnaker.orca.q.metrics.MessagePushed
import com.netflix.spinnaker.orca.q.metrics.MessageRescheduled
import com.netflix.spinnaker.orca.q.metrics.MessageRetried
import com.netflix.spinnaker.orca.q.metrics.MonitorableQueue
import com.netflix.spinnaker.orca.q.metrics.QueuePolled
import com.netflix.spinnaker.orca.q.metrics.QueueState
import com.netflix.spinnaker.orca.q.metrics.RetryPolled
import com.netflix.spinnaker.orca.q.metrics.fire
import com.netflix.spinnaker.orca.q.redis.migration.ExecutionTypeDeserializer
import com.netflix.spinnaker.orca.q.redis.migration.ExecutionTypeSerializer
import org.funktionale.partials.partially1
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCommands
import redis.clients.jedis.Transaction
import redis.clients.jedis.params.sortedset.ZAddParams
import redis.clients.util.Pool
import java.io.IOException
import java.nio.charset.Charset
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import java.time.temporal.TemporalAmount

class RedisQueue(
  private val queueName: String,
  private val pool: Pool<Jedis>,
  private val clock: Clock,
  private val lockTtlSeconds: Int = 10,
  override val ackTimeout: TemporalAmount = Duration.ofMinutes(1),
  override val deadMessageHandler: (Queue, Message) -> Unit,
  override val publisher: ApplicationEventPublisher
) : MonitorableQueue {

  private val mapper = ObjectMapper()
    .registerModule(KotlinModule())
    .disable(FAIL_ON_UNKNOWN_PROPERTIES)
    .registerModule(
      SimpleModule()
        .addSerializer(ExecutionTypeSerializer())
        .addDeserializer(ExecutionType::class.java, ExecutionTypeDeserializer())
    )

  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val queueKey = "$queueName.queue"
  private val unackedKey = "$queueName.unacked"
  private val messagesKey = "$queueName.messages"
  private val locksKey = "$queueName.locks"

  // TODO: use AttemptsAttribute instead
  private val attemptsKey = "$queueName.attempts"

  // TODO: legacy id support
  private val hashKey = "$queueName.hash"
  private val hashesKey = "$queueName.hashes"

  override fun poll(callback: (Message, () -> Unit) -> Unit) {
    pool.resource.use { redis ->
      redis.zrangeByScore(queueKey, 0.0, score(), 0, 1)
        .firstOrNull()
        ?.takeIf { fingerprint ->
          redis.acquireLock(fingerprint)
        }
        ?.also { fingerprint ->
          val ack = this::ackMessage.partially1(fingerprint)
          redis.readMessage(fingerprint) { message ->
            val attempts = message.getAttribute<AttemptsAttribute>()?.attempts ?: 0
            val maxAttempts = message.getAttribute<MaxAttemptsAttribute>()?.maxAttempts ?: 0

            if (maxAttempts > 0 && attempts > maxAttempts) {
              log.warn("Message $fingerprint with payload $message exceeded $maxAttempts retries")
              handleDeadMessage(message)
              redis.removeMessage(fingerprint)
              fire<MessageDead>()
            } else {
              callback(message, ack)
            }
          }
        }
      fire<QueuePolled>()
    }
  }

  override fun push(message: Message, delay: TemporalAmount) {
    pool.resource.use { redis ->
      val fingerprint = message.hash()
      if (redis.zismember(queueKey, fingerprint)) {
        log.warn("Re-prioritizing message as an identical one is already on the queue: $fingerprint, message: $message")
        redis.zadd(queueKey, score(delay), fingerprint)
        fire<MessageDuplicate>(message)
      } else {
        redis.queueMessage(message, delay)
        fire<MessagePushed>(message)
      }
    }
  }

  override fun reschedule(message: Message, delay: TemporalAmount) {
    pool.resource.use { redis ->
      val fingerprint = message.hash()
      log.debug("Re-scheduling message: $message, fingerprint: $fingerprint to deliver in $delay")
      val status: Long = redis.zadd(queueKey, score(delay), fingerprint, ZAddParams.zAddParams().xx())
      if (status.toInt() == 1){
        fire<MessageRescheduled>(message)
      } else {
        fire<MessageNotFound>(message)
      }
    }
  }

  @Scheduled(fixedDelayString = "\${queue.retry.frequency.ms:10000}")
  override fun retry() {
    pool.resource.use { redis ->
      redis
        .zrangeByScore(unackedKey, 0.0, score())
        .let { fingerprints ->
          if (fingerprints.size > 0) {
            fingerprints
              .map { "$locksKey:$it" }
              .let { redis.del(*it.toTypedArray()) }
          }

          fingerprints.forEach { fingerprint ->
            val attempts = redis.hgetInt(attemptsKey, fingerprint)
            if (attempts >= Queue.maxRetries) {
              redis.readMessage(fingerprint) { message ->
                log.warn("Message $fingerprint with payload $message exceeded max retries")
                handleDeadMessage(message)
                redis.removeMessage(fingerprint)
              }
              fire<MessageDead>()
            } else {
              if (redis.zismember(queueKey, fingerprint)) {
                redis
                  .multi {
                    zrem(unackedKey, fingerprint)
                    zadd(queueKey, score(), fingerprint)
                    // we only need to read the message for metrics purposes
                    hget(messagesKey, fingerprint)
                  }
                  .let { (_, _, json) ->
                    mapper
                      .readValue<Message>(json as String)
                      .let { message ->
                        log.warn("Not retrying message $fingerprint because an identical message is already on the queue")
                        fire<MessageDuplicate>(message)
                      }
                  }
              } else {
                log.warn("Retrying message $fingerprint after $attempts attempts")
                redis.requeueMessage(fingerprint)
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

  private fun ackMessage(fingerprint: String) {
    pool.resource.use { redis ->
      if (redis.zismember(queueKey, fingerprint)) {
        // only remove this message from the unacked queue as a matching one has
        // been put on the main queue
        redis.multi {
          zrem(unackedKey, fingerprint)
          del("$locksKey:$fingerprint")
        }
      } else {
        redis.removeMessage(fingerprint)
      }
      fire<MessageAcknowledged>()
    }
  }

  private fun Jedis.queueMessage(message: Message, delay: TemporalAmount = ZERO) {
    val fingerprint = message.hash()

    // ensure the message has the attempts tracking attribute
    message.setAttribute(
      message.getAttribute(AttemptsAttribute())
    )

    multi {
      hset(messagesKey, fingerprint, mapper.writeValueAsString(message))
      zadd(queueKey, score(delay), fingerprint)

      // TODO: legacy id compatibility
      hset(hashKey, fingerprint, fingerprint)
      sadd(hashesKey, fingerprint)
    }
  }

  private fun Jedis.requeueMessage(fingerprint: String) {
    val hash = hget(hashKey, fingerprint)
    multi {
      zrem(unackedKey, fingerprint)
      zadd(queueKey, score(), fingerprint)

      // TODO: legacy id compatibility
      if (hash != null) {
        sadd(hashesKey, hash)
      }
    }
  }

  private fun Jedis.removeMessage(fingerprint: String) {
    multi {
      zrem(queueKey, fingerprint)
      zrem(unackedKey, fingerprint)
      hdel(messagesKey, fingerprint)
      del("$locksKey:$fingerprint")

      // TODO: use AttemptAttribute instead
      hdel(attemptsKey, fingerprint)

      // TODO: legacy id compatibility
      hdel(hashKey, fingerprint)
    }
  }

  /**
   * Tries to read the message with the specified [fingerprint] passing it to
   * [block]. If it's not accessible for whatever reason any references are
   * cleaned up.
   */
  private fun Jedis.readMessage(fingerprint: String, block: (Message) -> Unit) {
    val hash = hget(hashKey, fingerprint)
    multi {
      hget(messagesKey, fingerprint)
      zrem(queueKey, fingerprint)
      zadd(unackedKey, score(ackTimeout), fingerprint)

      // TODO: legacy id compatibility
      srem(hashesKey, fingerprint)
      if (hash != null) {
        srem(hashesKey, hash)
      }

      // TODO: use AttemptsAttribute instead
      hincrBy(attemptsKey, fingerprint, 1)
    }.let {
      val json = it[0] as String?
      if (json == null) {
        log.error("Payload for message $fingerprint is missing")
        // clean up what is essentially an unrecoverable message
        removeMessage(fingerprint)
      } else {
        try {
          val message = mapper.readValue<Message>(json)

          // TODO: AttemptsAttribute could replace `attemptsKey`
          message.setAttribute(
            message.getAttribute(AttemptsAttribute())
          ).increment()
          hset(messagesKey, fingerprint, mapper.writeValueAsString(message))

          block.invoke(message)
        } catch (e: IOException) {
          log.error("Failed to read message $fingerprint, requeuing...", e)
          requeueMessage(fingerprint)
        }
      }
    }
  }

  private fun handleDeadMessage(message: Message) {
    deadMessageHandler.invoke(this, message)
  }

  private fun Jedis.acquireLock(fingerprint: String) =
    (set("$locksKey:$fingerprint", "\uD83D\uDD12", "NX", "EX", lockTtlSeconds) == "OK")
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

  private fun JedisCommands.zismember(key: String, member: String) =
    zrank(key, member) != null

  private fun Message.hash() =
    Hashing
      .murmur3_128()
      .hashString(toString(), Charset.defaultCharset())
      .toString()
}
