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
package com.netflix.spinnaker.keel.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.keel.ActivityRecord
import com.netflix.spinnaker.keel.IntentActivityRepository
import com.netflix.spinnaker.keel.model.ListCriteria
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class RedisIntentActivityRepository(
  redisClientSelector: RedisClientSelector,
  private val keelProperties: KeelProperties,
  private val objectMapper: ObjectMapper
) : AbstractRedisRepository(redisClientSelector), IntentActivityRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  override val clientName = "intentActivity"

  @PostConstruct fun init() {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun record(activity: ActivityRecord) {
    logKey(activity.intentId).let { key ->
      getClientForId(key).withCommandsClient { c ->
        c.rpush(key, objectMapper.writeValueAsString(activity))

        // Prune old log entries
        c.ltrim(key, 0, keelProperties.maxConvergenceLogEntriesPerIntent - 1L)
      }
    }
  }

  override fun getHistory(intentId: String, criteria: ListCriteria): List<ActivityRecord> =
    logKey(intentId).let { key ->
      getClientForId(key).withCommandsClient<List<ActivityRecord>> { c ->
        c.lrange(key, criteria.offset.toLong(), criteria.offset - 1L)
          .map { objectMapper.readValue<ActivityRecord>(it) }
      }
    }

  override fun <T : ActivityRecord> getHistory(intentId: String, kind: Class<T>, criteria: ListCriteria): List<T> =
    logKey(intentId).let { key ->
      getClientForId(key).withCommandsClient<List<T>> { c ->
        c.lrange(key, 0, c.llen(key) - 1)
          .map { objectMapper.readValue<ActivityRecord>(it) }
          .filterIsInstance(kind)
          .let { limitOffset(it, criteria) }
      }
    }

  private fun <T> limitOffset(list: Collection<T>, criteria: ListCriteria): List<T> {
    val size = list.size
    return if (size <= criteria.offset) {
      listOf()
    } else {
      var lastIndex = criteria.offset + criteria.limit
      if (lastIndex >= size) {
        lastIndex = size
      }
      list.toList().subList(criteria.offset, lastIndex)
    }
  }
}

internal fun logKey(intentId: String) = "log:$intentId"
