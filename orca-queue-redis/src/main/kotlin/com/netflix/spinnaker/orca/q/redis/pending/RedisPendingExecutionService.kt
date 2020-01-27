/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.q.redis.pending

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.q.Message
import redis.clients.jedis.Jedis
import redis.clients.jedis.util.Pool

class RedisPendingExecutionService(
  private val pool: Pool<Jedis>,
  private val mapper: ObjectMapper
) : PendingExecutionService {
  override fun enqueue(pipelineConfigId: String, message: Message) {
    pool.resource.use { redis ->
      redis.lpush(listName(pipelineConfigId), mapper.writeValueAsString(message))
    }
  }

  override fun popOldest(pipelineConfigId: String): Message? =
    pool.resource.use { redis ->
      redis
        .rpop(listName(pipelineConfigId))
        ?.let { mapper.readValue(it) }
    }

  override fun popNewest(pipelineConfigId: String): Message? =
    pool.resource.use { redis ->
      redis
        .lpop(listName(pipelineConfigId))
        ?.let { mapper.readValue(it) }
    }

  override fun purge(pipelineConfigId: String, callback: (Message) -> Unit) {
    pool.resource.use { redis ->
      while (redis.llen(listName(pipelineConfigId)) > 0L) {
        popOldest(pipelineConfigId)?.let(callback)
      }
    }
  }

  override fun depth(pipelineConfigId: String): Int =
    pool.resource.use { redis ->
      redis.llen(listName(pipelineConfigId)).toInt()
    }

  private fun listName(pipelineConfigId: String) =
    "orca.pipeline.queue.$pipelineConfigId"
}
