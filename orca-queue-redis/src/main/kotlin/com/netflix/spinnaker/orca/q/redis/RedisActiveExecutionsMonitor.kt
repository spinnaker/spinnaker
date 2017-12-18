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
import com.netflix.spinnaker.orca.events.ExecutionComplete
import com.netflix.spinnaker.orca.events.ExecutionEvent
import com.netflix.spinnaker.orca.events.ExecutionStarted
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import java.util.concurrent.ConcurrentHashMap
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
  @Qualifier("jedisPool") private val pool: Pool<Jedis>,
  private val objectMapper: ObjectMapper,
  private val registry: Registry
) : ApplicationListener<ExecutionEvent> {

  private val log = LoggerFactory.getLogger(javaClass)

  private val REDIS_KEY = "monitor.activeExecutions"

  private val snapshot: MutableMap<Id, AtomicLong> = ConcurrentHashMap()

  @Scheduled(fixedRateString = "\${queue.monitor.activeExecutions.register.frequency.ms:60000}")
  fun registerGauges() {
    snapshotActivity().also { executions ->
      log.info("Registering new active execution gauges (active: ${executions.size})")

      executions
        .map { it.getMetricId() }
        .filter { expectedId -> registry.gauges().noneMatch { it.id() == expectedId } }
        .forEach { registry.gauge(it, snapshot[it]) }
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

  @Scheduled(fixedDelayString = "\${queue.monitor.activeExecutions.cleanup.frequency.ms:300000}")
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
      pool.resource.use { redis ->
        redis.hdel(REDIS_KEY, *orphans)
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

    pool.resource.use { redis ->
      redis.hset(REDIS_KEY, execution.id, objectMapper.writeValueAsString(ActiveExecution(
        id = execution.id,
        type = execution.type,
        application = execution.application
      )))
    }
  }

  private fun completeExecution(executionId: String) {
    pool.resource.use { redis ->
      redis.hdel(REDIS_KEY, executionId)
    }
  }

  private fun getActiveExecutions() =
    pool.resource.use { redis ->
      redis.hgetAll(REDIS_KEY).map { objectMapper.readValue(it.value, ActiveExecution::class.java) }
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
