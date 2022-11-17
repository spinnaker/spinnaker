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
import com.netflix.spinnaker.config.OrcaSqlProperties
import com.netflix.spinnaker.config.TopApplicationExecutionCleanupAgentConfigurationProperties
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.notifications.scheduling.PipelineDependencyCleanupOperator
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import java.util.concurrent.atomic.AtomicInteger
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.name
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConditionalOnExpression("\${pollers.top-application-execution-cleanup.enabled:false} && \${execution-repository.sql.enabled:false}")
@EnableConfigurationProperties(TopApplicationExecutionCleanupAgentConfigurationProperties::class, OrcaSqlProperties::class)
class TopApplicationExecutionCleanupPollingNotificationAgent(
  clusterLock: NotificationClusterLock,
  private val jooq: DSLContext,
  registry: Registry,
  private val executionRepository: ExecutionRepository,
  private val configurationProperties: TopApplicationExecutionCleanupAgentConfigurationProperties,
  private val orcaSqlProperties: OrcaSqlProperties,
  private val pipelineDependencyCleanupOperators: List<PipelineDependencyCleanupOperator>
) : AbstractCleanupPollingAgent(
  clusterLock,
  configurationProperties.intervalMs,
  registry
) {

  override fun performCleanup() {
    // We don't have an index on partition/application so a query on a given partition is very expensive.
    // Instead perform a query without partition constraint to get potential candidates.
    // Then use the results of this candidate query to perform partial queries with partition set
    val candidateApplicationGroups = jooq
      .select(DSL.field("application"))
      .from(DSL.table("orchestrations"))
      .groupBy(DSL.field("application"))
      .having(DSL.count(DSL.field("id")).gt(configurationProperties.threshold))
      .fetch(DSL.field("application"), String::class.java)
      .groupBy { app ->
        configurationProperties.exceptionApplicationThresholds[app] ?: configurationProperties.threshold
      }

    candidateApplicationGroups.forEach { (thresholdToUse, candidateApplications) ->
      for (chunk in candidateApplications.chunked(5)) {
        val applicationsWithLotsOfOrchestrations = jooq
          .select(DSL.field("application"))
          .from(DSL.table("orchestrations"))
          .where(
            if (orcaSqlProperties.partitionName == null) {
              DSL.noCondition()
            } else {
              DSL.field(name("partition")).eq(orcaSqlProperties.partitionName)
            }
          )
          .and(DSL.field("application").`in`(*chunk.toTypedArray()))
          .groupBy(DSL.field("application"))
          .having(DSL.count(DSL.field("id")).gt(thresholdToUse))
          .fetch(DSL.field("application"), String::class.java)

        applicationsWithLotsOfOrchestrations
          .filter { !it.isNullOrEmpty() }
          .forEach { application ->
            try {
              val startTime = System.currentTimeMillis()

              log.debug("Cleaning up old orchestrations for $application")
              val deletedOrchestrationCount = performCleanup(application, thresholdToUse)
              log.debug(
                "Cleaned up {} old orchestrations for {} in {}ms",
                deletedOrchestrationCount,
                application,
                System.currentTimeMillis() - startTime
              )
            } catch (e: Exception) {
              log.error("Failed to cleanup old orchestrations for $application", e)
              errorsCounter.increment()
            }
          }
      }
    }
  }

  /**
   * An application can have at most [threshold] completed orchestrations.
   */
  private fun performCleanup(application: String, threshold: Int): Int {
    val deletedExecutionCount = AtomicInteger()

    val executionsToRemove = jooq
      .select(DSL.field("id"))
      .from(DSL.table("orchestrations"))
      .where(
        DSL.field("application").eq(application)
          .and(DSL.field("status").`in`(*completedStatuses.toTypedArray()))
      )
      .orderBy(DSL.field("build_time").desc())
      .limit(threshold, Int.MAX_VALUE)
      .fetch(DSL.field("id"), String::class.java)

    log.debug("Found {} old orchestrations for {}", executionsToRemove.size, application)

    pipelineDependencyCleanupOperators.forEach { it.cleanup(executionsToRemove) }

    executionsToRemove.chunked(configurationProperties.chunkSize).forEach { ids ->
      deletedExecutionCount.addAndGet(ids.size)

      executionRepository.delete(ExecutionType.ORCHESTRATION, ids)
      registry.counter(deletedId.withTag("application", application)).add(ids.size.toDouble())
    }

    return deletedExecutionCount.toInt()
  }
}
