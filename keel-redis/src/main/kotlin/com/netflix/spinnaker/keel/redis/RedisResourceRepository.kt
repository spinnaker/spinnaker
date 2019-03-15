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
import com.netflix.spinnaker.keel.api.UID
import com.netflix.spinnaker.keel.persistence.NoSuchResourceName
import com.netflix.spinnaker.keel.persistence.NoSuchResourceUID
import com.netflix.spinnaker.keel.persistence.ResourceHeader
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceState
import com.netflix.spinnaker.keel.persistence.ResourceState.Unknown
import com.netflix.spinnaker.keel.persistence.ResourceStateHistoryEntry
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import de.huxhorn.sulky.ulid.ULID
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisCommands
import java.time.Clock
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
        .map(ULID::parseULID)
        .forEach { uid ->
          // TODO: this is inefficient as fuck, store apiVersion and kind in indices
          readResource(redis, uid, Any::class.java)
            .also {
              callback(
                ResourceHeader(uid, it.metadata.name, it.metadata.resourceVersion, it.apiVersion, it.kind)
              )
            }
        }
    }
  }

  override fun <T : Any> get(uid: UID, specType: Class<T>): Resource<T> =
    redisClient.withCommandsClient<Resource<T>> { redis: JedisCommands ->
      readResource(redis, uid, specType)
    }

  override fun <T : Any> get(name: ResourceName, specType: Class<T>): Resource<T> =
    redisClient.withCommandsClient<Resource<T>> { redis: JedisCommands ->
      redis.hget(NAME_TO_UID_HASH, name.value)
        ?.let(ULID::parseULID)
        ?.let { uid -> readResource(redis, uid, specType) }
        ?: throw NoSuchResourceName(name)
    }

  override fun store(resource: Resource<*>) {
    redisClient.withCommandsClient<Unit> { redis: JedisCommands ->
      redis.hmset(resource.metadata.uid.key, resource.toHash())
      redis.sadd(INDEX_SET, resource.metadata.uid.toString())
      redis.hset(NAME_TO_UID_HASH, resource.metadata.name.value, resource.metadata.uid.toString())
      redis.lpush(resource.metadata.uid.stateKey, objectMapper.writeValueAsString(ResourceStateHistoryEntry(Unknown, clock.instant())))
    }
  }

  override fun delete(name: ResourceName) {
    redisClient.withCommandsClient<Unit> { redis: JedisCommands ->
      val uid = redis.hget(NAME_TO_UID_HASH, name.value).let(ULID::parseULID)
      redis.del(uid.key)
      redis.srem(INDEX_SET, uid.toString())
      redis.hdel(NAME_TO_UID_HASH, name.value)
    }
  }

  override fun lastKnownState(uid: UID): ResourceStateHistoryEntry =
    redisClient.withCommandsClient<ResourceStateHistoryEntry> { redis: JedisCommands ->
      redis.lindex(uid.stateKey, 0)
        .let { objectMapper.readValue(it) }
    }

  override fun stateHistory(uid: UID): List<ResourceStateHistoryEntry> =
    redisClient.withCommandsClient<List<ResourceStateHistoryEntry>> { redis: JedisCommands ->
      redis.lrange(uid.stateKey, 0, -1)
        .map { objectMapper.readValue<ResourceStateHistoryEntry>(it) }
    }

  override fun updateState(uid: UID, state: ResourceState) {
    redisClient.withCommandsClient<Unit> { redis: JedisCommands ->
      // TODO: probably not super thread safe
      redis.lindex(uid.stateKey, 0)
        ?.let { objectMapper.readValue<ResourceStateHistoryEntry>(it) }
        ?.state
        .also {
          if (it != state) {
            redis.lpush(uid.stateKey, objectMapper.writeValueAsString(ResourceStateHistoryEntry(state, clock.instant())))
          }
        }
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
    private const val NAME_TO_UID_HASH = "keel.resource.names"
    private const val INDEX_SET = "keel.resources"
    private const val RESOURCE_HASH = "{keel.resource.%s}"
    private const val STATE_SORTED_SET = "$RESOURCE_HASH.state"
  }

  private fun <T : Any> readResource(redis: JedisCommands, uid: UID, specType: Class<T>): Resource<T> =
    if (redis.sismember(INDEX_SET, uid.toString())) {
      redis.hgetAll(uid.key).let {
        Resource<T>(
          apiVersion = ApiVersion(it.getValue("apiVersion")),
          metadata = ResourceMetadata(objectMapper.readValue<Map<String, Any?>>(it.getValue("metadata"))),
          kind = it.getValue("kind"),
          spec = objectMapper.readValue(it.getValue("spec"), specType)
        )
      }
    } else {
      throw NoSuchResourceUID(uid)
    }

  private fun onInvalidIndex(uid: UID) {
    log.error("Invalid index entry {}", uid)
    redisClient.withCommandsClient<Long> { redis: JedisCommands ->
      redis.srem(INDEX_SET, uid.toString())
    }
  }

  private val UID.key: String
    get() = RESOURCE_HASH.format(this)

  private val UID.stateKey: String
    get() = STATE_SORTED_SET.format(this)

  private fun Resource<*>.toHash(): Map<String, String> = mapOf(
    "apiVersion" to apiVersion.toString(),
    "name" to metadata.name.value,
    "metadata" to objectMapper.writeValueAsString(objectMapper.convertValue<Map<String, Any?>>(metadata)),
    "kind" to kind,
    "spec" to objectMapper.writeValueAsString(spec)
  )
}

