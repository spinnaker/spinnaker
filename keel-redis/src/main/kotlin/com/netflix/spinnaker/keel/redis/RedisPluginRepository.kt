/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.registry.AssetType
import com.netflix.spinnaker.keel.registry.PluginAddress
import com.netflix.spinnaker.keel.registry.PluginRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import redis.clients.jedis.JedisPool
import javax.annotation.PostConstruct

@Component
class RedisPluginRepository(private val redisPool: JedisPool) : PluginRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

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

  override fun assetPluginFor(type: AssetType): PluginAddress? =
    redisPool.resource.use { redis ->
      redis.hget("keel.plugins.asset", type.serialized)
        ?.let { objectMapper.readValue(it) }
    }

  override fun addAssetPluginFor(type: AssetType, address: PluginAddress) {
    redisPool.resource.use { redis ->
      redis.hset("keel.plugins.asset", type.serialized, address.serialized)
    }
  }

  @PostConstruct
  fun logKnownPlugins() {
    redisPool.resource.use { redis ->
      redis
        .smembers("keel.plugins.veto")
        .map { objectMapper.readValue<PluginAddress>(it) }
        .forEach { log.info("Veto plugin at {}", it) }
      redis
        .hgetAll("keel.plugins.asset")
        .map { (type, address) ->
          with(objectMapper) {
            readValue<PluginAddress>(type) to readValue<PluginAddress>(address)
          }
        }
        .forEach { log.info("Asset plugin for {} at {}", it.first, it.second) }
    }
  }

  private val Any.serialized: String
    get() = objectMapper.writeValueAsString(this)
}
