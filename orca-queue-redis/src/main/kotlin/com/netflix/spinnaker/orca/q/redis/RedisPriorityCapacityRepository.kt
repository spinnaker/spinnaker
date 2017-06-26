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

  private val key = "queue:trafficShaping:priorityCapacity:capacity"

  override fun incrementExecutions(priority: Priority) {
    pool.resource.use { redis ->
      redis.hincrBy(key, priority.name, 1)
    }
  }

  override fun decrementExecutions(priority: Priority) {
    pool.resource.use { redis ->
      redis.hincrBy(key, priority.name, -1)
    }
  }

  override fun getGlobalCapacity(): GlobalCapacity {
    pool.resource.use { redis ->
      val capacity = redis.hgetAll(key) ?: return GlobalCapacity(ceiling = properties.capacity, criticalUsage = 0, highUsage = 0, mediumUsage = 0, lowUsage = 0)
      return GlobalCapacity(
        ceiling = capacity.getOrDefault("ceiling", properties.capacity).toString().toInt(),
        criticalUsage = capacity.getOrDefault(Priority.CRITICAL.name, 0).toString().toInt(),
        highUsage = capacity.getOrDefault(Priority.HIGH.name, 0).toString().toInt(),
        mediumUsage = capacity.getOrDefault(Priority.MEDIUM.name, 0).toString().toInt(),
        lowUsage = capacity.getOrDefault(Priority.LOW.name, 0).toString().toInt(),
        learning = if (capacity.containsKey("learning")) capacity["learning"]!!.toBoolean() else null
      )
    }
  }
}
