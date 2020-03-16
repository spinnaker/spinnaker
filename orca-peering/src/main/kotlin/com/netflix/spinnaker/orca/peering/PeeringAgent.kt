/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import org.slf4j.LoggerFactory
import kotlin.math.max

/**
 *
 */
class PeeringAgent(
  /**
   * ID of our peer
   */
  private val peeredId: String,

  /**
   * Interval in ms at which this agent runs
   */
  private val pollingIntervalMs: Long,

  /**
   * Maximum allowed clock drift when performing comparison of which executions need to be copied
   * For example: it's possible we take a snapshot of the src db and the latest updated_at is 1000
   * because not all orca instances are fully clock synchronized another instance might mutate an execution after we take
   * the snapshot but its clock might read 998
   */
  private val clockDriftMs: Long,

  /**
   * Source (our peers) database access layer
   */
  private val srcDB: SqlRawAccess,

  /**
   * Destination (our own) database access layer
   */
  private val destDB: SqlRawAccess,

  /**
   * Used to dynamically turn off either all of peering or peering of a specific host
   */
  private val dynamicConfigService: DynamicConfigService,

  private val peeringMetrics: PeeringMetrics,

  private val executionCopier: ExecutionCopier,

  clusterLock: NotificationClusterLock
) : AbstractPollingNotificationAgent(clusterLock) {

  private val log = LoggerFactory.getLogger(javaClass)
  private var completedPipelinesMostRecentUpdatedTime = 0L
  private var completedOrchestrationsMostRecentUpdatedTime = 0L
  private var runCount = 0

  override fun tick() {
    // Temporary "hack" to replicate deletes, for now we run a full diff (very expensive) every 10 ticks of the agent
    // Better solution forth coming
    if (runCount++ > 10) {
      completedOrchestrationsMostRecentUpdatedTime = 0L
      completedPipelinesMostRecentUpdatedTime = 0L
      runCount = 0
    }

    if (dynamicConfigService.isEnabled("pollers.peering", true) &&
      dynamicConfigService.isEnabled("pollers.peering.$peeredId", true)) {
      peerExecutions(ExecutionType.PIPELINE)
      peerExecutions(ExecutionType.ORCHESTRATION)
    }
  }

  private fun peerExecutions(executionType: ExecutionType) {
    val mostRecentUpdatedTime = when (executionType) {
      ExecutionType.ORCHESTRATION -> completedOrchestrationsMostRecentUpdatedTime
      ExecutionType.PIPELINE -> completedPipelinesMostRecentUpdatedTime
    }
    val isFirstRun = mostRecentUpdatedTime == 0L

    // On first copy of completed executions, there is no point in copying active executions
    // because they will be woefully out of date (since the first bulk copy will likely take 20+ minutes)
    if (isFirstRun) {
      peerCompletedExecutions(executionType)
    } else {
      peeringMetrics.recordLag(executionType) {
        peerCompletedExecutions(executionType)
        peerActiveExecutions(executionType)
      }
    }
  }

  /**
   * Migrate running/active executions of given type
   */
  private fun peerActiveExecutions(executionType: ExecutionType) {
    log.debug("Starting active $executionType copy for peering")

    val activePipelineIds = srcDB.getActiveExecutionIds(executionType, peeredId)
      .plus(srcDB.getActiveExecutionIds(executionType, null))

    if (activePipelineIds.isNotEmpty()) {
      log.debug("Found ${activePipelineIds.size} active $executionType, copying all")
      val migrationResult = executionCopier.copyInParallel(executionType, activePipelineIds, ExecutionState.ACTIVE)

      if (migrationResult.hadErrors) {
        log.error("Finished active $executionType peering: copied ${migrationResult.count} of ${activePipelineIds.size} with errors, see prior log statements")
      } else {
        log.debug("Finished active $executionType peering: copied ${migrationResult.count} of ${activePipelineIds.size}")
      }
    } else {
      log.debug("No active $executionType executions to copy for peering")
    }
  }

  /**
   * Migrate completed executions of given type
   */
  private fun peerCompletedExecutions(executionType: ExecutionType) {
    val updatedAfter = when (executionType) {
      ExecutionType.ORCHESTRATION -> completedOrchestrationsMostRecentUpdatedTime
      ExecutionType.PIPELINE -> completedPipelinesMostRecentUpdatedTime
    }

    log.debug("Starting completed $executionType copy for peering with $executionType updatedAfter=$updatedAfter")

    val newLatestUpdateTime = doMigrate(executionType, updatedAfter) - clockDriftMs

    if (executionType == ExecutionType.ORCHESTRATION) {
      completedOrchestrationsMostRecentUpdatedTime = max(0, newLatestUpdateTime)
    } else {
      completedPipelinesMostRecentUpdatedTime = max(0, newLatestUpdateTime)
    }
  }

  private fun doMigrate(executionType: ExecutionType, updatedAfter: Long): Long {
    // Compute diff
    val completedPipelineKeys = srcDB.getCompletedExecutionIds(executionType, peeredId, updatedAfter)
      .plus(srcDB.getCompletedExecutionIds(executionType, null, updatedAfter))
    val migratedPipelineKeys = destDB.getCompletedExecutionIds(executionType, peeredId, updatedAfter)

    val completedPipelineKeysMap = completedPipelineKeys
      .map { it.id to it }
      .toMap()
    val migratedPipelineKeysMap = migratedPipelineKeys
      .map { it.id to it }
      .toMap()

    val pipelineIdsToMigrate = completedPipelineKeys
      .filter { key -> migratedPipelineKeysMap[key.id]?.updated_at ?: 0 < key.updated_at }
      .map { it.id }
      .toList()

    val pipelineIdsToDelete = migratedPipelineKeys
      .filter { key -> !completedPipelineKeysMap.containsKey(key.id) }
      .map { it.id }
      .toList()

    fun getLatestCompletedUpdatedTime() =
      (completedPipelineKeys.map { it.updated_at }.max() ?: 0)

    if (pipelineIdsToDelete.isEmpty() && pipelineIdsToMigrate.isEmpty()) {
      log.debug("No completed $executionType executions to copy for peering")
      return getLatestCompletedUpdatedTime()
    }

    log.debug("Found ${completedPipelineKeys.size} completed $executionType candidates with ${migratedPipelineKeys.size} already copied for peering, ${pipelineIdsToMigrate.size} still need copying and ${pipelineIdsToDelete.size} need to be deleted")

    val maxDeleteCount = dynamicConfigService.getConfig(Integer::class.java, "pollers.peering.max-allowed-delete-count", Integer(100))
    var actualDeleted = pipelineIdsToDelete.size

    if (pipelineIdsToDelete.size > maxDeleteCount.toInt()) {
      log.error("Number of pipelines to delete (${pipelineIdsToDelete.size}) > threshold ($maxDeleteCount) - not performing deletes - if this is expected you can set the pollers.peering.max-allowed-delete-count property to a larger number")
      peeringMetrics.incrementNumErrors(executionType)
      actualDeleted = 0
    } else if (pipelineIdsToDelete.any()) {
      destDB.deleteExecutions(executionType, pipelineIdsToDelete)
      peeringMetrics.incrementNumDeleted(executionType, pipelineIdsToDelete.size)
    }

    if (!pipelineIdsToMigrate.any()) {
      log.debug("Finished completed $executionType peering: nothing to copy, $actualDeleted deleted")
      return getLatestCompletedUpdatedTime()
    }

    val migrationResult = executionCopier.copyInParallel(executionType, pipelineIdsToMigrate, ExecutionState.COMPLETED)
    if (migrationResult.hadErrors) {
      log.error("Finished completed $executionType peering: copied ${migrationResult.count} of ${pipelineIdsToMigrate.size} (deleted $actualDeleted) with errors, see prior log statements")
      return updatedAfter
    }

    log.debug("Finished completed $executionType peering: copied ${migrationResult.count} of ${pipelineIdsToMigrate.size} (deleted $actualDeleted) with latest updatedAt=${migrationResult.latestUpdatedAt}")
    return migrationResult.latestUpdatedAt
  }

  override fun getPollingInterval() = pollingIntervalMs
  override fun getNotificationType(): String = this.javaClass.simpleName
}
