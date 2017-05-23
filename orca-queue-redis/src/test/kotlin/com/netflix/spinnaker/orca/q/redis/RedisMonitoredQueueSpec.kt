/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.q.redis

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.q.DeadMessageCallback
import com.netflix.spinnaker.orca.q.metrics.MonitoredQueueSpec
import java.time.Clock

object RedisMonitoredQueueSpec : MonitoredQueueSpec<RedisQueue>(
  ::createQueue,
  RedisQueue::retry,
  ::shutdownCallback
)

private var redis: EmbeddedRedis? = null

private fun createQueue(
  clock: Clock,
  deadLetterCallback: DeadMessageCallback,
  registry: Registry
): RedisQueue {
  redis = EmbeddedRedis.embed()
  return RedisQueue(
    queueName = "test",
    pool = redis!!.pool,
    clock = clock,
    currentInstanceId = "i-1234",
    deadMessageHandler = deadLetterCallback,
    registry = registry
  )
}

private fun shutdownCallback() {
  println("shutting down the redis")
  redis?.destroy()
}

