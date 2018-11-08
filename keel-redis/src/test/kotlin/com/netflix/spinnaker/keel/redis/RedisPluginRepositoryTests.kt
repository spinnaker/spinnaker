package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.keel.registry.PluginRepositoryTests
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import redis.clients.jedis.Jedis

@TestInstance(PER_CLASS)
internal object RedisPluginRepositoryTests : PluginRepositoryTests<RedisPluginRepository>() {

  private lateinit var embeddedRedis: EmbeddedRedis
  private lateinit var redisClient: RedisClientDelegate

  override fun factory(): RedisPluginRepository =
    RedisPluginRepository(redisClient)

  override fun clear(subject: RedisPluginRepository) {
    redisClient.withCommandsClient { redis -> (redis as Jedis).flushAll() }
  }

  @BeforeAll
  fun startRedis() {
    embeddedRedis = EmbeddedRedis.embed()
    redisClient = JedisClientDelegate(embeddedRedis.pool)
  }

  @AfterAll
  fun stopRedis() {
    embeddedRedis.destroy()
  }
}
