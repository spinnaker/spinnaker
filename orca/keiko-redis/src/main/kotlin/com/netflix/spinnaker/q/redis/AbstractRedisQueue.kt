package com.netflix.spinnaker.q.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.common.hash.Hashing
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import com.netflix.spinnaker.q.migration.SerializationMigrator
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.temporal.TemporalAmount
import java.util.Optional
import org.slf4j.Logger
import redis.clients.jedis.Jedis
import redis.clients.jedis.Transaction
import redis.clients.jedis.commands.JedisClusterCommands
import redis.clients.jedis.commands.JedisCommands

abstract class AbstractRedisQueue(
  private val clock: Clock,
  private val lockTtlSeconds: Int = 10,
  private val mapper: ObjectMapper,
  private val serializationMigrator: Optional<SerializationMigrator>,
  override val ackTimeout: TemporalAmount = Duration.ofMinutes(1),
  override val deadMessageHandlers: List<DeadMessageCallback>,
  override val canPollMany: Boolean = false,
  override val publisher: EventPublisher

) : MonitorableQueue {
  internal abstract val queueKey: String
  internal abstract val unackedKey: String
  internal abstract val messagesKey: String
  internal abstract val locksKey: String
  internal abstract val attemptsKey: String

  internal abstract val log: Logger

  // Internal ObjectMapper that enforces deterministic property ordering for use only in hashing.
  private val hashObjectMapper = ObjectMapper().copy().apply {
    enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
  }

  abstract fun cacheScript()
  abstract var readMessageWithLockScriptSha: String

  internal fun runSerializationMigration(json: String): String {
    if (serializationMigrator.isPresent) {
      return serializationMigrator.get().migrate(json)
    }
    return json
  }

  internal fun handleDeadMessage(message: Message) {
    deadMessageHandlers.forEach {
      it.invoke(this, message)
    }
  }

  /**
   * @return current time (plus optional [delay]) converted to a score for a
   * Redis sorted set.
   */
  internal fun score(delay: TemporalAmount = Duration.ZERO) =
    clock.instant().plus(delay).toEpochMilli().toDouble()

  internal inline fun <reified R> ObjectMapper.readValue(content: String): R =
    readValue(content, R::class.java)

  internal fun Jedis.multi(block: Transaction.() -> Unit) =
    multi().use { tx ->
      tx.block()
      tx.exec()
    }

  internal fun JedisCommands.hgetInt(key: String, field: String, default: Int = 0) =
    hget(key, field)?.toInt() ?: default

  internal fun JedisClusterCommands.hgetInt(key: String, field: String, default: Int = 0) =
    hget(key, field)?.toInt() ?: default

  internal fun JedisCommands.zismember(key: String, member: String) =
    zrank(key, member) != null

  internal fun JedisClusterCommands.zismember(key: String, member: String) =
    zrank(key, member) != null

  internal fun JedisCommands.anyZismember(key: String, members: Set<String>) =
    members.any { zismember(key, it) }

  internal fun JedisClusterCommands.anyZismember(key: String, members: Set<String>) =
    members.any { zismember(key, it) }

  internal fun JedisCommands.firstFingerprint(key: String, fingerprint: Fingerprint) =
    fingerprint.all.firstOrNull { zismember(key, it) }

  internal fun JedisClusterCommands.firstFingerprint(key: String, fingerprint: Fingerprint) =
    fingerprint.all.firstOrNull { zismember(key, it) }

  @Deprecated("Hashes the attributes property, which is mutable")
  internal fun Message.hashV1() =
    Hashing
      .murmur3_128()
      .hashString(toString(), Charset.defaultCharset())
      .toString()

  internal fun Message.hashV2() =
    hashObjectMapper.convertValue(this, MutableMap::class.java)
      .apply { remove("attributes") }
      .let {
        Hashing
          .murmur3_128()
          .hashString("v2:${hashObjectMapper.writeValueAsString(it)}", StandardCharsets.UTF_8)
          .toString()
      }

  internal fun Message.fingerprint() =
    hashV2().let { Fingerprint(latest = it, all = setOf(it, hashV1())) }

  internal data class Fingerprint(
    val latest: String,
    val all: Set<String> = setOf()
  )
}

internal const val READ_MESSAGE_SRC =
  """
  local java_scientific = function(x)
    return string.format("%.12E", x):gsub("\+", "")
  end

  -- get the message, move the fingerprint to the unacked queue and return
  local message = redis.call("HGET", messagesKey, fingerprint)

  -- check for an ack timeout override on the message
  local unackScore = unackDefaultScore
  if type(message) == "string" and message ~= nil then
    local ackTimeoutOverride = tonumber(cjson.decode(message)["ackTimeoutMs"])
    if ackTimeoutOverride ~= nil and unackBaseScore ~= nil then
      unackScore = unackBaseScore + ackTimeoutOverride
    end
  end

  unackScore = java_scientific(unackScore)

  redis.call("ZREM", queueKey, fingerprint)
  redis.call("ZADD", unackKey, unackScore, fingerprint)
"""

/* ktlint-disable max-line-length */
internal const val READ_MESSAGE_WITH_LOCK_SRC =
  """
  local queueKey = KEYS[1]
  local unackKey = KEYS[2]
  local lockKey = KEYS[3]
  local messagesKey = KEYS[4]
  local maxScore = ARGV[1]
  local peekFingerprintCount = ARGV[2]
  local lockTtlSeconds = ARGV[3]
  local unackDefaultScore = ARGV[4]
  local unackBaseScore = ARGV[5]

  local not_empty = function(x)
    return (type(x) == "table") and (not x.err) and (#x ~= 0)
  end

  local acquire_lock = function(fingerprints, locksKey, lockTtlSeconds)
    if not_empty(fingerprints) then
      local i=1
      while (i <= #fingerprints) do
        redis.call("ECHO", "attempting lock on " .. fingerprints[i])
        if redis.call("SET", locksKey .. ":" .. fingerprints[i], "\uD83D\uDD12", "EX", lockTtlSeconds, "NX") then
          redis.call("ECHO", "acquired lock on " .. fingerprints[i])
          return fingerprints[i], fingerprints[i+1]
        end
        i=i+2
      end
    end
    return nil, nil
  end

  -- acquire a lock on a fingerprint
  local fingerprints = redis.call("ZRANGEBYSCORE", queueKey, 0.0, maxScore, "WITHSCORES", "LIMIT", 0, peekFingerprintCount)
  local fingerprint, fingerprintScore = acquire_lock(fingerprints, lockKey, lockTtlSeconds)

  -- no lock could be acquired
  if fingerprint == nil then
    if #fingerprints == 0 then
      return "NoReadyMessages"
    end
    return "AcquireLockFailed"
  end

  $READ_MESSAGE_SRC

  return {fingerprint, fingerprintScore, message}
"""
