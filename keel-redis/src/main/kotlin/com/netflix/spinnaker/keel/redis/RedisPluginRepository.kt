package com.netflix.spinnaker.keel.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.registry.PluginAddress
import com.netflix.spinnaker.keel.registry.PluginRepository
import org.springframework.stereotype.Component
import redis.clients.jedis.JedisPool

@Component
class RedisPluginRepository(private val redisPool: JedisPool) : PluginRepository {

  private val objectMapper = ObjectMapper()
    .apply { registerModule(KotlinModule()) }

  override fun vetoPlugins(): Iterable<PluginAddress> =
    redisPool.resource.use { redis ->
      redis
        .smembers("keel.plugins.veto")
        .map { objectMapper.readValue<PluginAddress>(it) }
    }

  override fun addVetoPlugin(address: PluginAddress) {
    redisPool.resource.use { redis ->
      redis.sadd("keel.plugins.veto", address.serialized)
    }
  }

  override fun assetPluginFor(type: TypeMetadata): PluginAddress? =
    redisPool.resource.use { redis ->
      redis.hget("keel.plugins.asset", type.serialized)
        ?.let { objectMapper.readValue(it) }
    }

  override fun addAssetPluginFor(type: TypeMetadata, address: PluginAddress) {
    redisPool.resource.use { redis ->
      redis.hset("keel.plugins.asset", type.serialized, address.serialized)
    }
  }

  private val TypeMetadata.serialized: String
    get() = objectMapper.writeValueAsString(mapOf("kind" to kind, "apiVersion" to apiVersion))

  private val PluginAddress.serialized: String
    get() = objectMapper.writeValueAsString(this)
}
