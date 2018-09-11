package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.keel.registry.PluginRepositoryTests
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import redis.clients.jedis.Jedis

internal object RedisPluginRepositoryTests : PluginRepositoryTests<RedisPluginRepository>(
  factory = ::createRedisRepository,
  clear = ::flushRedis,
  shutdownHook = embeddedRedis::destroy
)

private val embeddedRedis = EmbeddedRedis.embed()
private val redisClient = JedisClientDelegate(embeddedRedis.pool)

private fun createRedisRepository() = RedisPluginRepository(redisClient)

@Suppress("UNUSED_PARAMETER")
private fun flushRedis(repository: RedisPluginRepository) {
  redisClient.withCommandsClient { redis -> (redis as Jedis).flushAll() }
}
