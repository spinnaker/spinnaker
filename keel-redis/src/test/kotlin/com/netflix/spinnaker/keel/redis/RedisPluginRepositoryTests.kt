package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.keel.registry.PluginRepositoryTests
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import redis.clients.jedis.JedisPool

internal object RedisPluginRepositoryTests : PluginRepositoryTests<RedisPluginRepository>(
  factory = ::createRedisRepository,
  clear = ::flushRedis,
  shutdownHook = embeddedRedis::destroy
)

private val embeddedRedis = EmbeddedRedis.embed()
private val pool = embeddedRedis.pool as JedisPool

private fun createRedisRepository() = RedisPluginRepository(pool)

@Suppress("UNUSED_PARAMETER")
private fun flushRedis(repository: RedisPluginRepository) {
  pool.resource.use { redis -> redis.flushAll() }
}
