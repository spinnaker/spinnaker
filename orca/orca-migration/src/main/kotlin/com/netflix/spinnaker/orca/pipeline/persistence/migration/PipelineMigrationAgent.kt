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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.DualExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.slf4j.LoggerFactory

/**
 * Requires DualExecutionRepository being enabled to run migrations.
 */
class PipelineMigrationAgent(
  clusterLock: NotificationClusterLock,
  private val front50Service: Front50Service,
  private val dualExecutionRepository: DualExecutionRepository,
  private val pollingIntervalMs: Long
) : AbstractPollingNotificationAgent(clusterLock) {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun tick() {
    val previouslyMigratedPipelineIds = dualExecutionRepository.primary.retrieveAllExecutionIds(PIPELINE)

    val executionCriteria = ExecutionRepository.ExecutionCriteria().apply {
      pageSize = 50
      setStatuses(ExecutionStatus.COMPLETED.map { it.name })
    }

    val allPipelineConfigIds = front50Service.allPipelines.map { it["id"] as String }
      .toMutableList()
      .let { it + front50Service.allStrategies.map { it["id"] as String } }
    log.info("Found ${allPipelineConfigIds.size} pipeline configs")

    allPipelineConfigIds.forEachIndexed { index, pipelineConfigId ->
      val unmigratedPipelines = dualExecutionRepository.previous
        .retrievePipelinesForPipelineConfigId(pipelineConfigId, executionCriteria)
        .filter { !previouslyMigratedPipelineIds.contains(it.id) }
        .toList()
        .blockingGet()

      if (unmigratedPipelines.isNotEmpty()) {
        log.info("${unmigratedPipelines.size} pipelines to migrate ($pipelineConfigId) [$index/${allPipelineConfigIds.size}]")

        unmigratedPipelines.forEach {
          dualExecutionRepository.primary.store(it)
        }

        log.info("${unmigratedPipelines.size} pipelines migrated ($pipelineConfigId) [$index/${allPipelineConfigIds.size}]")
      }
    }
  }

  override fun getPollingInterval() = pollingIntervalMs
  override fun getNotificationType() = "pipelineMigrator"
}
