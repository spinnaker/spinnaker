/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.q.redis

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.config.RedisExecutionLogProperties
import com.netflix.spinnaker.orca.log.ExecutionLogEntry
import com.netflix.spinnaker.orca.log.ExecutionLogRepository
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import java.time.Duration
import java.time.temporal.ChronoUnit

class RedisExecutionLogRepository(
  private val pool: Pool<Jedis>,
  redisExecutionLogProperties: RedisExecutionLogProperties
) : ExecutionLogRepository {

  private val ttlSeconds = Duration.of(redisExecutionLogProperties.ttlDays, ChronoUnit.DAYS).seconds.toInt()

  private val objectMapper = ObjectMapper().apply {
    registerModule(KotlinModule())
    registerModule(JavaTimeModule())
    disable(FAIL_ON_UNKNOWN_PROPERTIES)
    setSerializationInclusion(NON_NULL)
  }

  override fun save(entry: ExecutionLogEntry) {
    val serializedEntry = objectMapper.writeValueAsString(entry)
    val key = "executionLog.${entry.executionId}"

    pool.resource.use { redis ->
      redis.zadd(key, entry.timestamp.toEpochMilli().toDouble(), serializedEntry)
      redis.expire(key, ttlSeconds)
    }
  }

  override fun getAllByExecutionId(executionId: String) =
    pool.resource.use { redis ->
      redis
        .zrangeByScore("executionLog.$executionId", "-inf", "+inf")
        .map { objectMapper.readValue(it, ExecutionLogEntry::class.java) }
    }
}
