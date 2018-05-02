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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.events.ExecutionEvent
import com.netflix.spinnaker.orca.events.ExecutionStarted
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * A monitor that records active executions.
 *
 * On every ExecutionStarted event, a record is added to a Redis hash by its execution ID. In the background, the
 * monitor will update an in-memory snapshot of active executions and register dynamic gauges into Spectator.
 *
 * Lastly a slower background job will iterate through all executions it knows about to verify that no active
 * executions have been orphaned by out-of-band failures.
 *
 * TODO rz - Add cloudProviders, accounts as tags
 */
@Component
class RedisActiveExecutionsMonitor(
  private val executionRepository: ExecutionRepository,
  @Qualifier("redisClientDelegate") private val redisClientDelegate: RedisClientDelegate,
  private val objectMapper: ObjectMapper,
  private val registry: Registry,
  @Value("\${queue.monitor.activeExecutions.refresh.frequency.ms:60000}") refreshFrequencyMs: Long,
  @Value("\${queue.monitor.activeExecutions.cleanup.frequency.ms:300000}") cleanupFrequencyMs: Long,
  @Value("\${queue.monitor.activeExecutions.key:monitor.activeExecutions}") val redisKey: String
) : ApplicationListener<ExecutionEvent> {

  private val log = LoggerFactory.getLogger(javaClass)

  private val snapshot: MutableMap<Id, AtomicLong> = ConcurrentHashMap()

  private val executor = Executors.newScheduledThreadPool(2)

  private val activePipelineCounter = registry.gauge(
    registry.createId("executions.active").withTag("executionType", Execution.ExecutionType.PIPELINE.toString()),
    AtomicInteger(0)
  )

  private val activeOrchestrationCounter = registry.gauge(
    registry.createId("executions.active").withTag("executionType", Execution.ExecutionType.ORCHESTRATION.toString()),
    AtomicInteger(0)
  )

  init {
    executor.scheduleWithFixedDelay(
      {
        try {
          refreshGauges()
        } catch (e : Exception) {
          log.error("Unable to refresh active execution gauges", e)
        }
      },
      0,
      refreshFrequencyMs,
      TimeUnit.MILLISECONDS
    )

    executor.scheduleWithFixedDelay(
      {
        try {
          cleanup()
        } catch (e : Exception) {
          log.error("Unable to cleanup orphaned active executions", e)
        }
      },
      0,
      cleanupFrequencyMs,
      TimeUnit.MILLISECONDS
    )
  }

  fun refreshGauges() {
    snapshotActivity().also { executions ->
      log.info("Refreshing active execution gauges (active: ${executions.size})")

      val executionByType = executions.groupBy { it.type }
      activePipelineCounter.set(executionByType.get(Execution.ExecutionType.PIPELINE)?.size ?: 0)
      activeOrchestrationCounter.set(executionByType.get(Execution.ExecutionType.ORCHESTRATION)?.size ?: 0)
    }
  }

  private fun snapshotActivity(): List<ActiveExecution> {
    val activeExecutions = getActiveExecutions()

    val working = mutableMapOf<Id, Long>()
    activeExecutions
      .map { it.getMetricId() }
      .forEach { working.computeIfAbsent(it, { 0L }).inc() }

    // Update snapshot from working copy
    working.forEach { snapshot.computeIfAbsent(it.key, { AtomicLong() }).set(it.value) }

    // Remove keys that are not in the working copy
    snapshot.keys
      .filterNot { working.containsKey(it) }
      .forEach { snapshot.remove(it) }

    return activeExecutions
  }

  fun cleanup() {
    val orphans = getActiveExecutions()
      .map {
        val execution: Execution
        try {
          execution = executionRepository.retrieve(it.type, it.id)
        } catch (e: ExecutionNotFoundException) {
          return@map it.id
        }
        return@map if (execution.status.isComplete) it.id else null
      }
      .filterNotNull()
      .toTypedArray()

    log.info("Cleaning up ${orphans.size} orphaned active executions")
    if (orphans.isNotEmpty()) {
      redisClientDelegate.withCommandsClient { redis ->
        redis.hdel(redisKey, *orphans)
      }
    }
  }

  override fun onApplicationEvent(event: ExecutionEvent) {
    if (event is ExecutionStarted) {
      startExecution(event.executionType, event.executionId)
    } else if (event is ExecutionComplete) {
      completeExecution(event.executionId)
    }
  }

  private fun startExecution(executionType: Execution.ExecutionType, executionId: String) {
    val execution: Execution
    try {
      execution = executionRepository.retrieve(executionType, executionId)
    } catch (e: ExecutionNotFoundException) {
      log.error("Received start execution event, but was unable to read execution from the database")
      return
    }

    redisClientDelegate.withCommandsClient{ redis ->
      redis.hset(redisKey, execution.id, objectMapper.writeValueAsString(ActiveExecution(
        id = execution.id,
        type = execution.type,
        application = execution.application
      )))
    }
  }

  private fun completeExecution(executionId: String) {
    redisClientDelegate.withCommandsClient { redis ->
      redis.hdel(redisKey, executionId)
    }
  }

  private fun getActiveExecutions() =
    redisClientDelegate.withCommandsClient<List<ActiveExecution>> { redis ->
      redis.hgetAll(redisKey).map { objectMapper.readValue(it.value, ActiveExecution::class.java) }
    }

  private fun ActiveExecution.getMetricId() =
    registry.createId("executions.active")
      .withTag("executionType", type.toString())

  data class ActiveExecution(
    val id: String,
    val type: Execution.ExecutionType,
    val application: String
  )
}
