package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.keel.persistence.ResourceVersionTracker
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate

class RedisResourceVersionTracker(
  private val redisClient: RedisClientDelegate
) : ResourceVersionTracker {
  companion object {
    const val KEY = "keel.k8s.resource.version"
  }

  override fun get(): Long =
    redisClient.withCommandsClient<Long> { redis ->
      redis.get(KEY)?.toLong() ?: 0L
    }

  override fun set(value: Long) {
    redisClient.withCommandsClient { redis ->
      redis.set(KEY, value.toString())
    }
  }
}
