package com.netflix.spinnaker.keel.redis

import com.netflix.spinnaker.keel.registry.PluginRepositoryTests
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import redis.clients.jedis.JedisPool

internal object RedisPluginRepositoryTests : PluginRepositoryTests<RedisPluginRepository>(
  ::createRedisRepository,
  embeddedRedis::destroy
)

val embeddedRedis = EmbeddedRedis.embed()
val jedisPool = embeddedRedis.pool as JedisPool

fun createRedisRepository() = RedisPluginRepository(jedisPool)
