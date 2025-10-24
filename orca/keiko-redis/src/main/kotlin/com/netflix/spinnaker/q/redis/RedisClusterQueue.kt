package com.netflix.spinnaker.q.redis

import arrow.core.partially1
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.KotlinOpen
import com.netflix.spinnaker.q.AttemptsAttribute
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.MaxAttemptsAttribute
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.QueueCallback
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.LockFailed
import com.netflix.spinnaker.q.metrics.MessageAcknowledged
import com.netflix.spinnaker.q.metrics.MessageDead
import com.netflix.spinnaker.q.metrics.MessageDuplicate
import com.netflix.spinnaker.q.metrics.MessageNotFound
import com.netflix.spinnaker.q.metrics.MessageProcessing
import com.netflix.spinnaker.q.metrics.MessagePushed
import com.netflix.spinnaker.q.metrics.MessageRescheduled
import com.netflix.spinnaker.q.metrics.MessageRetried
import com.netflix.spinnaker.q.metrics.MessageRetryFailed
import com.netflix.spinnaker.q.metrics.QueuePolled
import com.netflix.spinnaker.q.metrics.QueueState
import com.netflix.spinnaker.q.metrics.RetryPolled
import com.netflix.spinnaker.q.migration.SerializationMigrator
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.Locale
import java.util.Optional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.Transaction
import redis.clients.jedis.exceptions.JedisDataException
import redis.clients.jedis.params.ZAddParams.zAddParams
import redis.clients.jedis.util.JedisClusterCRC16

@KotlinOpen
class RedisClusterQueue(
  private val queueName: String,
  private val jedisCluster: JedisCluster,
  private val clock: Clock,
  private val lockTtlSeconds: Int = 10,
  private val mapper: ObjectMapper,
  private val serializationMigrator: Optional<SerializationMigrator>,
  override val ackTimeout: TemporalAmount = Duration.ofMinutes(1),
  override val deadMessageHandlers: List<DeadMessageCallback>,
  override val canPollMany: Boolean = false,
  override val publisher: EventPublisher
) : AbstractRedisQueue(
  clock,
  lockTtlSeconds,
  mapper,
  serializationMigrator,
  ackTimeout,
  deadMessageHandlers,
  canPollMany,
  publisher
) {

  final override val log: Logger = LoggerFactory.getLogger(javaClass)

  override val queueKey = "{$queueName}.queue"
  override val unackedKey = "{$queueName}.unacked"
  override val messagesKey = "{$queueName}.messages"
  override val locksKey = "{$queueName}.locks"
  override val attemptsKey = "{$queueName}.attempts"

  override lateinit var readMessageWithLockScriptSha: String

  init {
    cacheScript()
    log.info("Configured $javaClass queue: $queueName")
  }

  final override fun cacheScript() {
    readMessageWithLockScriptSha = jedisCluster.scriptLoad(READ_MESSAGE_WITH_LOCK_SRC, queueKey)
  }

  override fun poll(callback: (Message, () -> Unit) -> Unit) {
    jedisCluster.readMessageWithLock()
      ?.also { (fingerprint, scheduledTime, json) ->
        val ack = this::ackMessage.partially1(fingerprint)
        jedisCluster.readMessage(fingerprint, json) { message ->
          val attempts = message.getAttribute<AttemptsAttribute>()?.attempts
            ?: 0
          val maxAttempts = message.getAttribute<MaxAttemptsAttribute>()?.maxAttempts
            ?: 0

          if (maxAttempts > 0 && attempts > maxAttempts) {
            log.warn("Message $fingerprint with payload $message exceeded $maxAttempts retries")
            handleDeadMessage(message)
            jedisCluster.removeMessage(fingerprint)
            fire(MessageDead)
          } else {
            fire(MessageProcessing(message, scheduledTime, clock.instant()))
            callback(message, ack)
          }
        }
      }
    fire(QueuePolled)
  }

  override fun poll(maxMessages: Int, callback: QueueCallback) {
    poll(callback)
  }

  override fun push(message: Message, delay: TemporalAmount) {
    jedisCluster.firstFingerprint(queueKey, message.fingerprint()).also { fingerprint ->
      if (fingerprint != null) {
        log.info(
          "Re-prioritizing message as an identical one is already on the queue: " +
            "$fingerprint, message: $message"
        )
        jedisCluster.zadd(queueKey, score(delay), fingerprint, zAddParams().xx())
        fire(MessageDuplicate(message))
      } else {
        jedisCluster.queueMessage(message, delay)
        fire(MessagePushed(message))
      }
    }
  }

  override fun reschedule(message: Message, delay: TemporalAmount) {
    val fingerprint = message.fingerprint().latest
    log.debug("Re-scheduling message: $message, fingerprint: $fingerprint to deliver in $delay")
    val status: Long = jedisCluster.zadd(queueKey, score(delay), fingerprint, zAddParams().xx())
    if (status.toInt() == 1) {
      fire(MessageRescheduled(message))
    } else {
      fire(MessageNotFound(message))
    }
  }

  override fun ensure(message: Message, delay: TemporalAmount) {
    val fingerprint = message.fingerprint()
    if (!jedisCluster.anyZismember(queueKey, fingerprint.all) &&
      !jedisCluster.anyZismember(unackedKey, fingerprint.all)
    ) {
      log.debug(
        "Pushing ensured message onto queue as it does not exist in queue or unacked sets"
      )
      push(message, delay)
    }
  }

  @Scheduled(fixedDelayString = "\${queue.retry.frequency.ms:10000}")
  override fun retry() {
    jedisCluster
      .zrangeByScore(unackedKey, 0.0, score())
      .let { fingerprints ->
        if (fingerprints.size > 0) {
          fingerprints
            .map { "$locksKey:$it" }
            .let { jedisCluster.del(*it.toTypedArray()) }
        }

        fingerprints.forEach { fingerprint ->
          try {
            val attempts = jedisCluster.hgetInt(attemptsKey, fingerprint)
            jedisCluster.readMessageWithoutLock(fingerprint) { message ->
              val maxAttempts = message.getAttribute<MaxAttemptsAttribute>()?.maxAttempts ?: 0

              /* If maxAttempts attribute is set, let poll() handle max retry logic.
                 If not, check for attempts >= Queue.maxRetries - 1, as attemptsKey is now
                 only incremented when retrying unacked messages vs. by readMessage*() */
              if (maxAttempts == 0 && attempts >= Queue.maxRetries - 1) {
                log.warn("Message $fingerprint with payload $message exceeded max retries")
                handleDeadMessage(message)
                jedisCluster.removeMessage(fingerprint)
                fire(MessageDead)
              } else {
                if (jedisCluster.zismember(queueKey, fingerprint)) {
                  jedisCluster
                    .multi {
                      zrem(unackedKey, fingerprint)
                      zadd(queueKey, score(), fingerprint)
                      hincrBy(attemptsKey, fingerprint, 1L)
                    }
                  log.info(
                    "Not retrying message $fingerprint because an identical message " +
                      "is already on the queue"
                  )
                  fire(MessageDuplicate(message))
                } else {
                  log.warn("Retrying message $fingerprint after $attempts attempts")
                  jedisCluster.hincrBy(attemptsKey, fingerprint, 1L)
                  jedisCluster.requeueMessage(fingerprint)
                  fire(MessageRetried)
                }
              }
            }
          } catch (e: Throwable) {
            log.error("Caught unhandled exception while retrying unacked message $fingerprint." +
              "Ignoring it and proceeding with the rest of the messages in the unacked queue.", e)
            fire(MessageRetryFailed)
          }
        }
      }
      .also {
        fire(RetryPolled)
      }
  }

  override fun readState(): QueueState =
    jedisCluster.multi {
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

  override fun containsMessage(predicate: (Message) -> Boolean): Boolean {
    var found = false
    var cursor = "0"
    while (!found) {
      jedisCluster.hscan(messagesKey, cursor).apply {
        found = result
          .map { mapper.readValue<Message>(it.value) }
          .any(predicate)
        cursor = getCursor()
      }
      if (cursor == "0") break
    }
    return found
  }

  internal fun JedisCluster.queueMessage(
    message: Message,
    delay: TemporalAmount = Duration.ZERO
  ) {
    val fingerprint = message.fingerprint().latest

    // ensure the message has the attempts tracking attribute
    message.setAttribute(
      message.getAttribute() ?: AttemptsAttribute()
    )

    multi {
      hset(messagesKey, fingerprint, mapper.writeValueAsString(message))
      zadd(queueKey, score(delay), fingerprint)
    }
  }

  internal fun JedisCluster.requeueMessage(fingerprint: String) {
    multi {
      zrem(unackedKey, fingerprint)
      zadd(queueKey, score(), fingerprint)
    }
  }

  internal fun JedisCluster.removeMessage(fingerprint: String) {
    multi {
      zrem(queueKey, fingerprint)
      zrem(unackedKey, fingerprint)
      hdel(messagesKey, fingerprint)
      del("$locksKey:$fingerprint")
      hdel(attemptsKey, fingerprint)
    }
  }

  internal fun JedisCluster.readMessageWithoutLock(
    fingerprint: String,
    block: (Message) -> Unit
  ) {
    try {
      hget(messagesKey, fingerprint)
        .let {
          val message = mapper.readValue<Message>(runSerializationMigration(it))
          block.invoke(message)
        }
    } catch (e: IOException) {
      log.error("Failed to read unacked message $fingerprint, requeuing...", e)
      hincrBy(attemptsKey, fingerprint, 1L)
      requeueMessage(fingerprint)
    } catch (e: JsonParseException) {
      log.error("Payload for unacked message $fingerprint is missing or corrupt", e)
      removeMessage(fingerprint)
    }
  }

  internal fun JedisCluster.readMessageWithLock(): Triple<String, Instant, String?>? {
    try {
      val response = evalsha(
        readMessageWithLockScriptSha,
        listOf(
          queueKey,
          unackedKey,
          locksKey,
          messagesKey
        ),
        listOf(
          score().toString(),
          10.toString(), // TODO rz - make this configurable.
          lockTtlSeconds.toString(),
          java.lang.String.format(Locale.US, "%f", score(ackTimeout)),
          java.lang.String.format(Locale.US, "%f", score())
        )
      )
      if (response is List<*>) {
        return Triple(
          response[0].toString(), // fingerprint
          Instant.ofEpochMilli(response[1].toString().toLong()), // fingerprintScore
          response[2]?.toString() // message
        )
      }
      if (response == "ReadLockFailed") {
        // This isn't a "bad" thing, but means there's more work than keiko can process in a cycle
        // in this case, but may be a signal to tune `peekFingerprintCount`
        fire(LockFailed)
      }
    } catch (e: JedisDataException) {
      if ((e.message ?: "").startsWith("NOSCRIPT")) {
        cacheScript()
        return readMessageWithLock()
      } else {
        throw e
      }
    }
    return null
  }

  internal fun JedisCluster.readMessage(
    fingerprint: String,
    json: String?,
    block: (Message) -> Unit
  ) {
    if (json == null) {
      log.error("Payload for message $fingerprint is missing")
      // clean up what is essentially an unrecoverable message
      removeMessage(fingerprint)
    } else {
      try {
        val message = mapper.readValue<Message>(runSerializationMigration(json))
          .apply {
            val currentAttempts = (getAttribute() ?: AttemptsAttribute())
              .run { copy(attempts = attempts + 1) }
            setAttribute(currentAttempts)
          }

        hset(messagesKey, fingerprint, mapper.writeValueAsString(message))

        block.invoke(message)
      } catch (e: IOException) {
        log.error("Failed to read message $fingerprint, requeuing...", e)
        hincrBy(attemptsKey, fingerprint, 1L)
        requeueMessage(fingerprint)
      }
    }
  }

  fun JedisCluster.multi(block: Transaction.() -> Unit) =
    getConnectionFromSlot(JedisClusterCRC16.getSlot(queueKey))
      .use { c ->
        c.multi()
          .let { tx ->
            tx.block()
            tx.exec()
          }
      }

  private fun ackMessage(fingerprint: String) {
    if (jedisCluster.zismember(queueKey, fingerprint)) {
      // only remove this message from the unacked queue as a matching one has
      // been put on the main queue
      jedisCluster.multi {
        zrem(unackedKey, fingerprint)
        del("$locksKey:$fingerprint")
      }
    } else {
      jedisCluster.removeMessage(fingerprint)
    }
    fire(MessageAcknowledged)
  }
}
