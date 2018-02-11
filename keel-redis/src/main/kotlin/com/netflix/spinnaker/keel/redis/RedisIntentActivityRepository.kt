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
import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.keel.IntentActivityRepository
import com.netflix.spinnaker.keel.IntentConvergenceRecord
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Clock
import javax.annotation.PostConstruct

@Component
class RedisIntentActivityRepository
@Autowired constructor(
  @Qualifier("mainRedisClient") private val mainRedisClientDelegate: RedisClientDelegate,
  @Qualifier("previousRedisClient") private val previousRedisClientDelegate: RedisClientDelegate?,
  private val keelProperties: KeelProperties,
  private val objectMapper: ObjectMapper,
  private val clock: Clock
) : IntentActivityRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct fun init() {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun addOrchestration(intentId: String, orchestrationId: String)
    = addOrchestrations(intentId, listOf(orchestrationId))

  override fun addOrchestrations(intentId: String, orchestrations: List<String>) {
    val score = clock.millis()
    historyKey(intentId).let { key ->
      getClientForId(key).withCommandsClient { c ->
        c.zadd(key, orchestrations.map { parseOrchestrationId(it) to score.toDouble() }.toMap().toMutableMap())
      }
    }

    currentKey(intentId).let { key ->
      getClientForId(key).withCommandsClient { c ->
        c.zadd(key, orchestrations.map { parseOrchestrationId(it) to score.toDouble() }.toMap().toMutableMap())
      }
    }
  }

  override fun getHistory(intentId: String)
    = historyKey(intentId).let { key ->
        getClientForId(key).withCommandsClient<Set<String>> { c ->
          c.zrangeByScore(key, "-inf", "+inf")
        }
      }.toList()

  override fun logConvergence(intentConvergenceRecord: IntentConvergenceRecord) {
    val score = intentConvergenceRecord.timestampMillis // This timestampMillis is recorded when the record is created.
    logKey(intentConvergenceRecord.intentId). let { key ->
      getClientForId(key).withCommandsClient { c ->
        c.zadd(key, mapOf(objectMapper.writeValueAsString(intentConvergenceRecord) to score.toDouble()) )
      }
    }

    // If we're over the message limit, we need to drop log entries.
    logKey(intentConvergenceRecord.intentId). let { key ->
      getClientForId(key).withCommandsClient { c ->
        val count = c.zcount(key, "-inf", "+inf")
        val numMsgsLeft = keelProperties.maxConvergenceLogEntriesPerIntent - count
        if (numMsgsLeft < 0){
          // Set is sorted from lowest score to highest.
          // Set is scored by timestampMillis, which only grows as time increases.
          // Drop the lowest score messages.
          c.zremrangeByRank(key, 0, (-1*numMsgsLeft) - 1)
        }
      }
    }
  }

  override fun getLog(intentId: String): List<IntentConvergenceRecord>
    = logKey(intentId). let { key ->
        getClientForId(key).withCommandsClient<Set<String>> { c ->
          c.zrangeByScore(key, "-inf", "+inf")
        }
      }.map { objectMapper.readValue(it, IntentConvergenceRecord::class.java) }.toList()

  override fun getLogEntry(intentId: String, timestampMillis: Long)
    = logKey(intentId). let { key ->
        getClientForId(key).withCommandsClient<Set<String>> { c ->
          c.zrangeByScore(key, timestampMillis.toDouble(), timestampMillis.toDouble())
        }
      }.map { objectMapper.readValue(it, IntentConvergenceRecord::class.java) }
      .toList()
      .also {
        // The same intent shouldn't be processed more than once at the exact same time.
        if (it.size > 1) log.warn("Two messages with the same timestampMillis. This shouldn't happen.")
      }
      .firstOrNull()

  private fun getClientForId(id: String?): RedisClientDelegate {
    if (id == null) {
      return mainRedisClientDelegate
    }

    var client: RedisClientDelegate? = null
    mainRedisClientDelegate.withCommandsClient {
      if (it.exists(id)) {
        client = mainRedisClientDelegate
      }
    }

    if (client == null && previousRedisClientDelegate != null) {
      previousRedisClientDelegate.withCommandsClient {
        if (it.exists(id)) {
          client = previousRedisClientDelegate
        }
      }
    }

    return client ?: mainRedisClientDelegate
  }
}

internal fun historyKey(intentId: String) = "history:$intentId"

internal fun currentKey(intentId: String) = "current:$intentId"

internal fun logKey(intentId: String) = "log:$intentId"
