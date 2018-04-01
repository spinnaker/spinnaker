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
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.tracing.Trace
import com.netflix.spinnaker.keel.tracing.TraceRepository
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.*
import javax.annotation.PostConstruct

@Component
class RedisTraceRepository
@Autowired constructor(
  redisClientSelector: RedisClientSelector,
  private val objectMapper: ObjectMapper,
  private val clock: Clock
) : AbstractRedisRepository(redisClientSelector), TraceRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  override val clientName = "trace"

  @PostConstruct fun init() {
    log.info("Using ${javaClass.simpleName}")
  }

  override fun record(trace: Trace) {
    val createTs = clock.millis()
    traceKey().let { traceKey: String ->
      getClientForId(traceKey).withCommandsClient { c ->
        c.hmset(traceKey, mapOf(
          "intent" to objectMapper.writeValueAsString(trace.intent),
          "createTs" to createTs.toString(),
          "startingState" to objectMapper.writeValueAsString(trace.startingState)
        ))
        c.zadd(traceIndexKey(), createTs.toDouble(), traceKey)
        c.zadd(intentTracesKey(trace.intent.id()), createTs.toDouble(), traceKey)
      }
    }
  }

  // TODO rz - should include limits
  override fun getForIntent(intentId: String) =
    intentTracesKey(intentId)
      .let { key ->
        val client = getClientForId(key)
        client.withCommandsClient<Set<String>> {
          it.zrangeByScore(intentTracesKey(intentId), "-inf", "+inf")
        }
        .let { index ->
          client.withCommandsClient<List<Map<String, String>>> { c ->
            index.map { c.hgetAll(it) }
          }
        }
        .map {
          Trace(
            startingState = objectMapper.readValue<Map<String, Any>>(it["startingState"], ANY_MAP_TYPE),
            intent = objectMapper.readValue(it["intent"], Intent::class.java),
            createTs = it["createTs"]?.toLong()
          )
        }
      }
}

internal fun intentTracesKey(intentId: String) = "traces:$intentId"
internal fun traceIndexKey() = "traces:index"
internal fun traceKey() = "trace:${UUID.randomUUID()}"
