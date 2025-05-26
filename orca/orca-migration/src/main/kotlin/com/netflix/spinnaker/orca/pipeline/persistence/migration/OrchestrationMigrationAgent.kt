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
package com.netflix.spinnaker.orca.pipeline.persistence.migration

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.DualExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.slf4j.LoggerFactory

/**
 * Requires DualExecutionRepository being enabled to run migrations.
 */
class OrchestrationMigrationAgent(
  clusterLock: NotificationClusterLock,
  private val front50Service: Front50Service,
  private val dualExecutionRepository: DualExecutionRepository,
  private val pollingIntervalMs: Long
) : AbstractPollingNotificationAgent(clusterLock) {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun tick() {
    val previouslyMigratedOrchestrationIds = dualExecutionRepository.primary.retrieveAllExecutionIds(ORCHESTRATION)

    val executionCriteria = ExecutionRepository.ExecutionCriteria().apply {
      pageSize = 3500
      setStatuses(ExecutionStatus.COMPLETED.map { it.name })
    }

    val allApplications = front50Service.allApplications
    log.info("Found ${allApplications.size} applications")

    allApplications.forEachIndexed { index, application ->
      val applicationName = application.name.toLowerCase()
      val unmigratedOrchestrations = dualExecutionRepository.previous
        .retrieveOrchestrationsForApplication(applicationName, executionCriteria)
        .filter { !previouslyMigratedOrchestrationIds.contains(it.id) }
        .sorted { t1, t2 ->
          return@sorted when {
            t1.getRealStartTime() > t2.getRealStartTime() -> 1
            t1.getRealStartTime() < t2.getRealStartTime() -> -1
            else -> 0
          }
        }
        .take(1000)
        .toList()
        .blockingGet()

      if (unmigratedOrchestrations.isNotEmpty()) {
        log.info("${unmigratedOrchestrations.size} orchestrations to migrate ($applicationName) [$index/${allApplications.size}]")

        unmigratedOrchestrations.forEach {
          dualExecutionRepository.primary.store(it)
        }

        log.info("${unmigratedOrchestrations.size} orchestrations migrated ($applicationName) [$index/${allApplications.size}]")
      }
    }
  }

  private fun PipelineExecution.getRealStartTime(): Long {
    return if (startTime == null) {
      if (buildTime == null) Long.MAX_VALUE else buildTime!!
    } else {
      startTime!!
    }
  }

  override fun getPollingInterval() = pollingIntervalMs
  override fun getNotificationType() = "orchestrationMigrator"
}
