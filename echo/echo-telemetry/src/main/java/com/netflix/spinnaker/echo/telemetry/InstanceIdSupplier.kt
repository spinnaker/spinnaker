/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.telemetry

import com.netflix.spinnaker.echo.config.TelemetryConfig
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import com.netflix.spinnaker.kork.jedis.exception.RedisClientNotFound
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import redis.clients.jedis.commands.JedisCommands

val UNIQUE_ID_KEY = "spinnaker:stats:instanceId"

@Component
@ConditionalOnProperty(value = ["stats.enabled"], matchIfMissing = true)
class InstanceIdSupplier(
  private val config: TelemetryConfig.TelemetryConfigProps,
  redisSelector: RedisClientSelector?
) {
  private val log: Logger = LoggerFactory.getLogger(this.javaClass)
  private val redis = try { redisSelector?.primary("default") } catch (e: RedisClientNotFound) { null }
  val uniqueId by lazy { getOrSetId(config.instanceId) }

  /**
   * Returns the current instance id for this Spinnaker instance. If no id is currently set,
   * sets it to [newId] and returns [newId].
   */
  private fun getOrSetId(newId: String): String {
    return try {
      redis?.withCommandsClient<String> { c: JedisCommands ->
        c.setnx(UNIQUE_ID_KEY, newId)
        c.get(UNIQUE_ID_KEY)
      }
    } catch (e: Exception) {
      log.warn("Error synchronizing unique instance id with redis", e)
      null
    } ?: newId
  }
}
