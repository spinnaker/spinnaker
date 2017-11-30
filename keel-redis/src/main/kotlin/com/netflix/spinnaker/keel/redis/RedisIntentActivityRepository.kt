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

import com.netflix.spinnaker.keel.IntentActivityRepository
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

  override fun getCurrent(intentId: String): List<String>
    = currentKey(intentId).let { key ->
        getClientForId(key).withCommandsClient<Set<String>> { c ->
          c.zrangeByScore(key, "-inf", "+inf")
        }
      }.toList()

  override fun upsertCurrent(intentId: String, orchestrations: List<String>)
    = currentKey(intentId).let { key ->
        val score = clock.millis()
        getClientForId(key).withCommandsClient { c ->
          c.zadd(key, orchestrations.map { parseOrchestrationId(it) to score.toDouble() }.toMap().toMutableMap())
        }
      }


  override fun upsertCurrent(intentId: String, orchestration: String) {
    upsertCurrent(intentId, listOf(orchestration))
  }

  override fun removeCurrent(intentId: String, orchestrationId: String)
    = currentKey(intentId).let { key ->
        getClientForId(key).withCommandsClient { c ->
          c.zrem(key, orchestrationId)
        }
      }

  override fun removeCurrent(intentId: String)
    = currentKey(intentId).let { key ->
        getClientForId(key).withCommandsClient { c ->
          c.zrem(key)
        }
      }

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
