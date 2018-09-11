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
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import javax.annotation.PostConstruct

@Component
class RedisPluginRepository(
  @Qualifier("redisPool") private val redisPool: Pool<Jedis>
) : PluginRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val objectMapper = ObjectMapper()
    .apply { registerModule(KotlinModule()) }

  override fun allPlugins(): Iterable<PluginAddress> =
    inRedis { redis ->
      (redis.hvals("keel.plugins.asset") + redis.smembers("keel.plugins.veto"))
        .map { objectMapper.readValue<PluginAddress>(it) }
    }

  override fun assetPlugins(): Iterable<PluginAddress> =
    inRedis { redis ->
      redis
        .hvals("keel.plugins.asset")
        .map { objectMapper.readValue<PluginAddress>(it) }
    }

  override fun vetoPlugins(): Iterable<PluginAddress> =
    inRedis { redis ->
      redis
        .smembers("keel.plugins.veto")
        .map { objectMapper.readValue<PluginAddress>(it) }
    }

  override fun addVetoPlugin(address: PluginAddress) {
    inRedis { redis ->
      redis.sadd("keel.plugins.veto", address.serialized)
    }
  }

  override fun assetPluginFor(type: AssetType): PluginAddress? =
    inRedis { redis ->
      redis.hget("keel.plugins.asset", type.serialized)
        ?.let { objectMapper.readValue(it) }
    }

  override fun addAssetPluginFor(type: AssetType, address: PluginAddress) {
    inRedis { redis ->
      redis.hset("keel.plugins.asset", type.serialized, address.serialized)
    }
  }

  @PostConstruct
  fun logKnownPlugins() {
    inRedis { redis ->
      redis
        .smembers("keel.plugins.veto")
        .map { objectMapper.readValue<PluginAddress>(it) }
        .forEach { log.info("'{}' veto plugin at {}, port {}", it.name, it.vip, it.port) }
      redis
        .hgetAll("keel.plugins.asset")
        .map { (type, address) ->
          with(objectMapper) {
            readValue<AssetType>(type) to readValue<PluginAddress>(address)
          }
        }
        .forEach { (type, address) ->
          log.info("'{}' asset plugin supporting {} at {}, port {}", address.name, type, address.vip, address.port)
        }
    }
  }

  private val Any.serialized: String
    get() = objectMapper.writeValueAsString(this)

  private fun <T> inRedis(operation: (Jedis) -> T): T =
    redisPool.resource.use(operation)
}
