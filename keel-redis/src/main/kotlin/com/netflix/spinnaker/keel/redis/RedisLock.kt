package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.keel.sync.Lock
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import java.time.Clock
import java.time.Duration

class RedisLock(
  private val redisClient: RedisClientDelegate,
  private val clock: Clock
) : Lock {
  override fun tryAcquire(name: String, duration: Duration): Boolean =
    redisClient.withCommandsClient<Boolean> { redis ->
      redis.set("lock:$name", "🔒", "NX", "PX", duration.toMillis()) == "OK"
    }
}
