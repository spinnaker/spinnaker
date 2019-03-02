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
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceState
import com.netflix.spinnaker.keel.persistence.ResourceState.Unknown
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisCommands
import java.time.Clock
import java.time.Instant
import javax.annotation.PostConstruct

class RedisResourceRepository(
  private val redisClient: RedisClientDelegate,
  private val objectMapper: ObjectMapper,
  private val clock: Clock
) : ResourceRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun allResources(callback: (ResourceHeader) -> Unit) {
    redisClient.withCommandsClient<Unit> { redis: JedisCommands ->
      redis.smembers(INDEX_SET)
        .map(::ResourceName)
        .forEach { name ->
          // TODO: this is inefficient as fuck, store apiVersion and kind in indices
          readResource(redis, name, Any::class.java)
            .also {
              callback(
                ResourceHeader(it.metadata.uid!!, it.metadata.name, it.metadata.resourceVersion, it.apiVersion, it.kind)
              )
            }
        }
    }
  }

  override fun <T : Any> get(name: ResourceName, specType: Class<T>): Resource<T> =
    redisClient.withCommandsClient<Resource<T>> { redis: JedisCommands ->
      readResource(redis, name, specType)
    }

  override fun store(resource: Resource<*>) {
    redisClient.withCommandsClient<Long> { redis: JedisCommands ->
      redis.hmset(resource.metadata.name.key, resource.toHash())
      redis.sadd(INDEX_SET, resource.metadata.name.value)
      redis.zadd(resource.metadata.name.stateKey, timestamp(), Unknown.name)
    }
  }

  override fun delete(name: ResourceName) {
    redisClient.withCommandsClient<Long> { redis: JedisCommands ->
      redis.del(name.key)
      redis.srem(INDEX_SET, name.value)
    }
  }

  override fun lastKnownState(name: ResourceName): Pair<ResourceState, Instant> =
    redisClient.withCommandsClient<Pair<ResourceState, Instant>> { redis: JedisCommands ->
      redis.zrevrangeByScoreWithScores(name.stateKey, Double.MAX_VALUE, 0.0, 0, 1)
        .asSequence()
        .map { ResourceState.valueOf(it.element) to Instant.ofEpochMilli(it.score.toLong()) }
        .first()
    }

  override fun updateState(name: ResourceName, state: ResourceState) {
    redisClient.withCommandsClient<Long> { redis: JedisCommands ->
      redis.zadd(name.stateKey, timestamp(), state.name)
    }
  }

  @PostConstruct
  fun logKnownResources() {
    redisClient.withCommandsClient<Unit> { redis: JedisCommands ->
      redis
        .smembers(INDEX_SET)
        .sorted()
        .also { log.info("Managing the following resources:") }
        .forEach { log.info(" - $it") }
    }
  }

  companion object {
    private const val INDEX_SET = "keel.resources"
    private const val RESOURCE_HASH = "{keel.resource.%s}"
    private const val STATE_SORTED_SET = "$RESOURCE_HASH.state"
  }

  private fun <T : Any> readResource(redis: JedisCommands, name: ResourceName, specType: Class<T>): Resource<T> =
    if (redis.sismember(INDEX_SET, name.value)) {
      redis.hgetAll(name.key).let {
        Resource<T>(
          apiVersion = ApiVersion(it.getValue("apiVersion")),
          metadata = ResourceMetadata(objectMapper.readValue<Map<String, Any?>>(it.getValue("metadata"))),
          kind = it.getValue("kind"),
          spec = objectMapper.readValue(it.getValue("spec"), specType)
        )
      }
    } else {
      throw NoSuchResourceException(name)
    }

  private fun onInvalidIndex(name: ResourceName) {
    log.error("Invalid index entry {}", name)
    redisClient.withCommandsClient<Long> { redis: JedisCommands ->
      redis.srem(INDEX_SET, name.value)
    }
  }

  private val ResourceName.key: String
    get() = RESOURCE_HASH.format(value)

  private val ResourceName.stateKey: String
    get() = STATE_SORTED_SET.format(value)

  private fun Resource<*>.toHash(): Map<String, String> = mapOf(
    "apiVersion" to apiVersion.toString(),
    "name" to metadata.name.value,
    "metadata" to objectMapper.writeValueAsString(objectMapper.convertValue<Map<String, Any?>>(metadata)),
    "kind" to kind,
    "spec" to objectMapper.writeValueAsString(spec)
  )

  private fun timestamp() = clock.millis().toDouble()
}

