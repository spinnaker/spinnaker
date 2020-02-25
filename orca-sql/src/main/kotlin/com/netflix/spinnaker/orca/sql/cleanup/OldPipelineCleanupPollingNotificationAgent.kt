/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.sql.cleanup

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.LongTaskTimer
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.sql.pipeline.persistence.transactional
import org.jooq.DSLContext
import org.jooq.impl.DSL.count
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger

@Component
@ConditionalOnExpression("\${pollers.old-pipeline-cleanup.enabled:false} && !\${execution-repository.redis.enabled:false}")
class OldPipelineCleanupPollingNotificationAgent(
  clusterLock: NotificationClusterLock,
  private val jooq: DSLContext,
  private val clock: Clock,
  private val registry: Registry,
  @Value("\${pollers.old-pipeline-cleanup.interval-ms:3600000}") private val pollingIntervalMs: Long,
  @Value("\${pollers.old-pipeline-cleanup.threshold-days:30}") private val thresholdDays: Long,
  @Value("\${pollers.old-pipeline-cleanup.minimum-pipeline-executions:5}") private val minimumPipelineExecutions: Int,
  @Value("\${pollers.old-pipeline-cleanup.chunk-size:1}") private val chunkSize: Int
) : AbstractPollingNotificationAgent(clusterLock) {

  companion object {
    internal val retrySupport = RetrySupport()
  }

  private val log = LoggerFactory.getLogger(OldPipelineCleanupPollingNotificationAgent::class.java)

  private val deletedId: Id
  private val errorsCounter: Counter
  private val invocationTimer: LongTaskTimer

  private val completedStatuses = ExecutionStatus.COMPLETED.map { it.toString() }

  init {
    deletedId = registry.createId("pollers.oldPipelineCleanup.deleted")
    errorsCounter = registry.counter("pollers.oldPipelineCleanup.errors")

    invocationTimer = com.netflix.spectator.api.patterns.LongTaskTimer.get(
      registry, registry.createId("pollers.oldPipelineCleanup.timing")
    )
  }

  override fun getPollingInterval(): Long {
    return pollingIntervalMs
  }

  override fun getNotificationType(): String {
    return OldPipelineCleanupPollingNotificationAgent::class.java.simpleName
  }

  override fun tick() {
    val timerId = invocationTimer.start()
    val startTime = System.currentTimeMillis()

    try {
      log.info("Agent {} started", notificationType)
      performCleanup()
    } catch (e: Exception) {
      log.error("Agent {} failed to perform cleanup", javaClass, e)
    } finally {
      log.info("Agent {} completed in {}ms", notificationType, System.currentTimeMillis() - startTime)
      invocationTimer.stop(timerId)
    }
  }

  private fun performCleanup() {
    val thresholdMillis = Instant.ofEpochMilli(clock.millis()).minus(thresholdDays, ChronoUnit.DAYS).toEpochMilli()

    val candidateApplications = jooq
      .select(field("application"))
      .from(table("pipelines"))
      .groupBy(field("application"))
      .having(count(field("id")).gt(minimumPipelineExecutions))
      .fetch(field("application"), String::class.java)

    for (chunk in candidateApplications.chunked(5)) {
      val pipelineConfigsWithOldExecutions = jooq
        .select(field("application"), field("config_id"), count(field("id")).`as`("count"))
        .from(table("pipelines"))
        .where(
          field("application").`in`(*chunk.toTypedArray()))
        .and(
          field("build_time").le(thresholdMillis))
        .and(
          field("status").`in`(*completedStatuses.toTypedArray()))
        .groupBy(field("application"), field("config_id"))
        .having(count(field("id")).gt(minimumPipelineExecutions))
        .fetch()

      pipelineConfigsWithOldExecutions.forEach {
        val application = it.getValue(field("application")) as String? ?: return@forEach
        val pipelineConfigId = it.getValue(field("config_id")) as String? ?: return@forEach

        try {
          val startTime = System.currentTimeMillis()

          log.debug("Cleaning up old pipelines for $application (pipelineConfigId: $pipelineConfigId)")
          val deletedPipelineCount = performCleanup(application, pipelineConfigId, thresholdMillis)
          log.debug(
            "Cleaned up {} old pipelines for {} in {}ms (pipelineConfigId: {})",
            deletedPipelineCount,
            application,
            System.currentTimeMillis() - startTime,
            pipelineConfigId
          )
        } catch (e: Exception) {
          log.error("Failed to cleanup old pipelines for $application (pipelineConfigId: $pipelineConfigId)", e)
          errorsCounter.increment()
        }
      }
    }
  }

  /**
   * Cleanup any execution of [pipelineConfigId] in [application] that is older than [thresholdMillis].
   *
   * _Only_ executions in a completed status are candidates for cleanup.
   *
   * In order to avoid removing _all_ executions of a particular pipeline configuration, [minimumPipelineExecutions] can
   * be used to indicate that a specific number of the most recent executions should be preserved even though they are
   * older than [thresholdMillis].
   */
  private fun performCleanup(
    application: String,
    pipelineConfigId: String,
    thresholdMillis: Long
  ): Int {
    val deletedExecutionCount = AtomicInteger()

    val executionsToRemove = jooq
      .select(field("id"))
      .from(table("pipelines"))
      .where(
        field("build_time").le(thresholdMillis).and(
          "status IN (${completedStatuses.joinToString(",") { "'$it'" }})"
        ).and(
          field("application").eq(application)
        ).and(
          field("config_id").eq(pipelineConfigId)
        )
      )
      .orderBy(field("build_time").desc())
      .limit(minimumPipelineExecutions, Int.MAX_VALUE)
      .fetch()
      .map { it.getValue(field("id")) }

    executionsToRemove.chunked(chunkSize).forEach { ids ->
      deletedExecutionCount.addAndGet(ids.size)
      jooq.transactional(retrySupport) {
        it.delete(table("correlation_ids")).where(
          "pipeline_id IN (${ids.joinToString(",") { "'$it'" }})"
        ).execute()
        it.delete(table("pipeline_stages")).where(
          "execution_id IN (${ids.joinToString(",") { "'$it'" }})"
        ).execute()
        it.delete(table("pipelines")).where(
          "id IN (${ids.joinToString(",") { "'$it'" }})"
        ).execute()
      }

      registry.counter(deletedId.withTag("application", application)).add(ids.size.toDouble())
    }

    return deletedExecutionCount.toInt()
  }
}
