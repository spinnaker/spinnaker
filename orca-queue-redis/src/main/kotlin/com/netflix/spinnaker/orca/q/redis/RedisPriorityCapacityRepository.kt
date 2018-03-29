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

import com.netflix.spinnaker.config.TrafficShapingProperties
import com.netflix.spinnaker.orca.q.trafficshaping.capacity.GlobalCapacity
import com.netflix.spinnaker.orca.q.trafficshaping.capacity.Priority
import com.netflix.spinnaker.orca.q.trafficshaping.capacity.PriorityCapacityRepository
import redis.clients.jedis.Jedis
import redis.clients.util.Pool

class RedisPriorityCapacityRepository(
  private val pool: Pool<Jedis>,
  private val properties: TrafficShapingProperties.PriorityCapacityProperties
) : PriorityCapacityRepository {

  private val key = "queue:trafficShaping:priorityCapacity"

  override fun incrementExecutions(executionId: String, priority: Priority) {
    pool.resource.use { redis ->
      redis.sadd(getPriorityKey(priority), executionId)
    }
  }

  override fun decrementExecutions(executionId: String, priority: Priority) {
    pool.resource.use { redis ->
      redis.srem(getPriorityKey(priority), executionId)
    }
  }

  override fun getGlobalCapacity(): GlobalCapacity {
    pool.resource.use { redis ->
      return GlobalCapacity(
        ceiling = (redis.get("$key:ceiling") ?: properties.capacity).toString().toInt(),
        criticalUsage = (redis.scard(getPriorityKey(Priority.CRITICAL)) ?: 0).toString().toInt(),
        highUsage = (redis.scard(getPriorityKey(Priority.HIGH)) ?: 0).toString().toInt(),
        mediumUsage = (redis.scard(getPriorityKey(Priority.MEDIUM)) ?: 0).toString().toInt(),
        lowUsage = (redis.scard(getPriorityKey(Priority.LOW)) ?: 0).toString().toInt(),
        learning = if (redis.exists("$key:learning")) redis.get("$key:learning").toBoolean() else null
      )
    }
  }

  private fun getPriorityKey(priority: Priority) = "$key:${priority.name.toLowerCase()}"
}
