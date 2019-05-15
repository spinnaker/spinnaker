package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.keel.persistence.ResourceRepositoryTests
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import org.junit.jupiter.api.AfterAll
import redis.clients.jedis.Jedis
import java.time.Clock

internal object RedisResourceRepositoryTests : ResourceRepositoryTests<RedisResourceRepository>() {

  override fun factory(clock: Clock) = RedisResourceRepository(
    redisClient,
    clock,
    configuredObjectMapper()
  )

  private val embeddedRedis = EmbeddedRedis.embed()
  private val redisClient = JedisClientDelegate(embeddedRedis.pool)

  override fun flush() {
    redisClient.withCommandsClient { redis -> (redis as Jedis).flushAll() }
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    embeddedRedis.destroy()
  }
}
