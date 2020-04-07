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
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/**
 * The orca agent that performs peering of executions across different orca DBs
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
  private var deletedExecutionCursor = 0

  override fun tick() {
    if (dynamicConfigService.isEnabled("pollers.peering", true) &&
      dynamicConfigService.isEnabled("pollers.peering.$peeredId", true)) {
      peeringMetrics.recordOverallLag() {
        peerExecutions(ExecutionType.PIPELINE)
        peerExecutions(ExecutionType.ORCHESTRATION)
        peerDeletedExecutions()
      }
    }
  }

  private fun peerExecutions(executionType: ExecutionType) {
    val start = Instant.now()

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
      peerCompletedExecutions(executionType)
      peerActiveExecutions(executionType)
    }

    peeringMetrics.recordLag(executionType, Duration.between(start, Instant.now()))
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

  /**
   * Propagate deletes
   * NOTE: ids of executions (both orchestrations and pipelines) that have been deleted are stored in the deleted_executions table
   * the "id/primarykey" on that table is an auto-incrementing int, so we use that as a "cursor" to know what we've deleted and
   * what still needs to be deleted.
   * There is no harm (just some wasted RDS CPU) to "deleting" an execution that doesn't exist
   */
  private fun peerDeletedExecutions() {
    val deletedExecutionIds = srcDB.getDeletedExecutions(deletedExecutionCursor)
    log.debug("Found ${deletedExecutionIds.size} deleted candidates after cursor: $deletedExecutionCursor")

    try {
      val orchestrationIdsToDelete = deletedExecutionIds.filter { it.execution_type == ExecutionType.ORCHESTRATION.toString() }.map { it.execution_id }
      val pipelineIdsToDelete = deletedExecutionIds.filter { it.execution_type == ExecutionType.PIPELINE.toString() }.map { it.execution_id }

      val orchestrationsDeleted = destDB.deleteExecutions(ExecutionType.ORCHESTRATION, orchestrationIdsToDelete)
      peeringMetrics.incrementNumDeleted(ExecutionType.ORCHESTRATION, orchestrationsDeleted)

      val pipelinesDeleted = destDB.deleteExecutions(ExecutionType.PIPELINE, pipelineIdsToDelete)
      peeringMetrics.incrementNumDeleted(ExecutionType.PIPELINE, pipelinesDeleted)

      deletedExecutionCursor = (deletedExecutionIds.maxBy { it.id })
        ?.id
        ?: deletedExecutionCursor

      // It is likely that some executions were deleted during "general" peering (e.g. in doMigrate), but most will be
      // deleted here so it's OK for the actual delete counts to not match the "requested" count
      log.debug("Deleted orchestrations: $orchestrationsDeleted (of ${orchestrationIdsToDelete.size} requested), pipelines: $pipelinesDeleted (of ${pipelineIdsToDelete.size} requested), new cursor: $deletedExecutionCursor")
    } catch (e: Exception) {
      log.error("Failed to delete some executions, not updating the cursor location to retry next time", e)
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

    val pipelineIdsToDelete = migratedPipelineKeys
      .filter { key -> !completedPipelineKeysMap.containsKey(key.id) }
      .map { it.id }

    fun getLatestCompletedUpdatedTime() =
      (completedPipelineKeys.map { it.updated_at }.max() ?: updatedAfter)

    if (pipelineIdsToDelete.isEmpty() && pipelineIdsToMigrate.isEmpty()) {
      log.debug("No completed $executionType executions to copy for peering")
      return getLatestCompletedUpdatedTime()
    }

    log.debug("Found ${completedPipelineKeys.size} completed $executionType candidates with ${migratedPipelineKeys.size} already copied for peering, ${pipelineIdsToMigrate.size} still need copying and ${pipelineIdsToDelete.size} need to be deleted")

    val maxDeleteCount = dynamicConfigService.getConfig(Integer::class.java, "pollers.peering.max-allowed-delete-count", Integer(100))
    var actualDeleted = 0

    if (pipelineIdsToDelete.size > maxDeleteCount.toInt()) {
      log.error("Number of pipelines to delete (${pipelineIdsToDelete.size}) > threshold ($maxDeleteCount) - not performing deletes - if this is expected you can set the pollers.peering.max-allowed-delete-count property to a larger number")
      peeringMetrics.incrementNumErrors(executionType)
    } else if (pipelineIdsToDelete.any()) {
      actualDeleted = destDB.deleteExecutions(executionType, pipelineIdsToDelete)
      peeringMetrics.incrementNumDeleted(executionType, actualDeleted)
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
