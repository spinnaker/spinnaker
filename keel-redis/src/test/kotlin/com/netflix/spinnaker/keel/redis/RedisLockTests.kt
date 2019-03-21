package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.keel.sync.LockTests
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import org.junit.jupiter.api.AfterAll
import redis.clients.jedis.Jedis
import java.time.Clock
import java.time.Duration

internal object RedisLockTests : LockTests<RedisLock>() {

  private val embeddedRedis = EmbeddedRedis.embed()
  private val redisClient = JedisClientDelegate(embeddedRedis.pool)

  override fun flush() {
    redisClient.withCommandsClient { redis -> (redis as Jedis).flushAll() }
  }

  override fun simulateTimePassing(name: String, duration: Duration) {
    redisClient.withCommandsClient { redis ->
      val ttl = redis.pttl("lock:$name") - duration.toMillis()
      redis.pexpire("lock:$name", Math.max(0L, ttl))
    }
  }

  override fun subject(clock: Clock): RedisLock {
    return RedisLock(redisClient, clock)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    embeddedRedis.destroy()
  }
}
