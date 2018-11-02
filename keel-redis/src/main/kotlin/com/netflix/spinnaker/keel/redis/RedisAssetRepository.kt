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
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetMetadata
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.AssetRepository
import com.netflix.spinnaker.keel.persistence.AssetState
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisCommands
import java.time.Clock
import java.time.Instant
import javax.annotation.PostConstruct

class RedisAssetRepository(
  private val redisClient: RedisClientDelegate,
  private val objectMapper: ObjectMapper,
  private val clock: Clock
) : AssetRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun allAssets(callback: (Asset) -> Unit) {
    withRedis { redis ->
      redis.smembers(INDEX_SET)
        .map(::AssetName)
        .forEach { name ->
          readAsset(redis, name)
            ?.also(callback)
            ?: onInvalidIndex(name)
        }
    }
  }

  override fun get(name: AssetName): Asset? =
    withRedis { redis ->
      readAsset(redis, name)
    }

  override fun store(asset: Asset) {
    withRedis { redis ->
      redis.hmset(asset.id.key, asset.toHash())
      redis.sadd(INDEX_SET, asset.id.value)
      redis.zadd(asset.id.stateKey, timestamp(), AssetState.Unknown.name)
    }
  }

  override fun delete(name: AssetName) {
    withRedis { redis ->
      redis.del(name.key)
      redis.srem(INDEX_SET, name.value)
    }
  }

  override fun lastKnownState(name: AssetName): Pair<AssetState, Instant>? =
    withRedis { redis ->
      redis.zrangeByScoreWithScores(name.stateKey, Double.MIN_VALUE, Double.MAX_VALUE, 0, 1)
        .asSequence()
        .map { AssetState.valueOf(it.element) to Instant.ofEpochMilli(it.score.toLong()) }
        .firstOrNull()
    }

  override fun updateState(name: AssetName, state: AssetState) {
    withRedis { redis ->
      redis.zadd(name.stateKey, timestamp(), state.name)
    }
  }

  @PostConstruct
  fun logKnownAssets() {
    withRedis { redis ->
      redis
        .smembers(INDEX_SET)
        .sorted()
        .also { log.info("Managing the following assets:") }
        .forEach { log.info(" - $it") }
    }
  }

  companion object {
    private const val INDEX_SET = "keel.assets"
    private const val ASSET_HASH = "{keel.asset.%s}"
    private const val STATE_SORTED_SET = "$ASSET_HASH.state"
  }

  private fun readAsset(redis: JedisCommands, name: AssetName): Asset? =
    if (redis.sismember(INDEX_SET, name.value)) {
      redis.hgetAll(name.key)?.let {
        Asset(
          apiVersion = ApiVersion(it.getValue("apiVersion")),
          metadata = AssetMetadata(
            name = name,
            data = objectMapper.readValue(it.getValue("metadata"))
          ),
          kind = it.getValue("kind"),
          spec = objectMapper.readValue(it.getValue("spec"))
        )
      }
    } else {
      null
    }

  private fun onInvalidIndex(name: AssetName) {
    log.error("Invalid index entry {}", name)
    withRedis { redis ->
      redis.srem(INDEX_SET, name.value)
    }
  }

  private fun <T> withRedis(operation: (JedisCommands) -> T): T =
    redisClient.withCommandsClient(operation)

  private val AssetName.key: String
    get() = ASSET_HASH.format(value)

  private val AssetName.stateKey: String
    get() = STATE_SORTED_SET.format(value)

  private fun Asset.toHash(): Map<String, String> = mapOf(
    "apiVersion" to apiVersion.toString(),
    "name" to metadata.name.value,
    "metadata" to objectMapper.writeValueAsString(metadata.data),
    "kind" to kind,
    "spec" to objectMapper.writeValueAsString(spec)
  )

  private fun timestamp() = clock.millis().toDouble()
}
