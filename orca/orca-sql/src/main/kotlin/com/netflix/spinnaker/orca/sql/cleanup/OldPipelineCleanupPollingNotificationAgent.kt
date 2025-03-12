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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.OldPipelineCleanupAgentConfigurationProperties
import com.netflix.spinnaker.config.OrcaSqlProperties
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.notifications.scheduling.PipelineDependencyCleanupOperator
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger
import org.jooq.DSLContext
import org.jooq.impl.DSL.count
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConditionalOnExpression("\${pollers.old-pipeline-cleanup.enabled:false} && \${execution-repository.sql.enabled:false}")
@EnableConfigurationProperties(OldPipelineCleanupAgentConfigurationProperties::class, OrcaSqlProperties::class)
class OldPipelineCleanupPollingNotificationAgent(
  clusterLock: NotificationClusterLock,
  private val jooq: DSLContext,
  private val clock: Clock,
  registry: Registry,
  private val executionRepository: ExecutionRepository,
  private val configurationProperties: OldPipelineCleanupAgentConfigurationProperties,
  private val orcaSqlProperties: OrcaSqlProperties,
  private val pipelineDependencyCleanupOperators: List<PipelineDependencyCleanupOperator>
) : AbstractCleanupPollingAgent(
  clusterLock,
  configurationProperties.intervalMs,
  registry
) {

  override fun performCleanup() {
    val exceptionalApps = configurationProperties.exceptionalApplications.toHashSet()

    val candidateApplicationsGroups = jooq
      .select(field("application"))
      .from(table("pipelines"))
      .groupBy(field("application"))
      .having(count(field("id")).gt(configurationProperties.minimumPipelineExecutions))
      .fetch(field("application"), String::class.java)
      .groupBy { app ->
        if (exceptionalApps.contains(app)) {
          configurationProperties.exceptionalApplicationsThresholdDays
        } else {
          configurationProperties.thresholdDays
        }
      }

    candidateApplicationsGroups.forEach { (thresholdToUse, candidateApplications) ->
      val thresholdMillis = Instant.ofEpochMilli(clock.millis()).minus(thresholdToUse, ChronoUnit.DAYS).toEpochMilli()

      for (chunk in candidateApplications.chunked(5)) {
        var queryBuilder = jooq
          .select(field("application"), field("config_id"), count(field("id")).`as`("count"))
          .from(table("pipelines"))
          .where(
            field("application").`in`(*chunk.toTypedArray())
          )
          .and(
            field("build_time").le(thresholdMillis)
          )
          .and(
            field("status").`in`(*completedStatuses.toTypedArray())
          )

        if (orcaSqlProperties.partitionName != null) {
          queryBuilder = queryBuilder
            .and(
              field(name("partition")).eq(orcaSqlProperties.partitionName)
            )
        }

        val pipelineConfigsWithOldExecutions = queryBuilder
          .groupBy(field("application"), field("config_id"))
          .having(count(field("id")).gt(configurationProperties.minimumPipelineExecutions))
          .fetch()

        pipelineConfigsWithOldExecutions.forEach doCleanup@{
          val application = it.getValue(field("application")) as String? ?: return@doCleanup
          val pipelineConfigId = it.getValue(field("config_id")) as String? ?: return@doCleanup

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

    var queryBuilder = jooq
      .select(field("id"))
      .from(table("pipelines"))
      .where(
        field("build_time").le(thresholdMillis)
          .and(field("status").`in`(*completedStatuses.toTypedArray()))
          .and(field("application").eq(application))
          .and(field("config_id").eq(pipelineConfigId))
      )

    if (orcaSqlProperties.partitionName != null) {
      queryBuilder = queryBuilder
        .and(
          field(name("partition")).eq(orcaSqlProperties.partitionName)
        )
    }

    val executionsToRemove = queryBuilder
      .orderBy(field("build_time").desc())
      .limit(configurationProperties.minimumPipelineExecutions, Int.MAX_VALUE)
      .fetch(field("id"), String::class.java)

    pipelineDependencyCleanupOperators.forEach { it.cleanup(executionsToRemove) }

    executionsToRemove.chunked(configurationProperties.chunkSize).forEach { ids ->
      deletedExecutionCount.addAndGet(ids.size)
      executionRepository.delete(ExecutionType.PIPELINE, ids)

      registry.counter(deletedId.withTag("application", application)).add(ids.size.toDouble())
    }

    return deletedExecutionCount.toInt()
  }
}
