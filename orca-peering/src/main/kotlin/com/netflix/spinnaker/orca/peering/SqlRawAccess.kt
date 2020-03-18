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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import org.jooq.Record
import org.jooq.Result
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class SqlRawAccess(
  val chunkSize: Int
) {
  val log: Logger = LoggerFactory.getLogger(this.javaClass)
  val completedStatuses = ExecutionStatus.COMPLETED.map { it.toString() }
  val activeStatuses = ExecutionStatus.values().map { it.toString() }.filter { !completedStatuses.contains(it) }

  /**
   *  Returns a list of execution IDs and their update_at times for completed executions
   */
  abstract fun getCompletedExecutionIds(executionType: ExecutionType, partitionName: String?, updatedAfter: Long): List<ExecutionDiffKey>

  /**
   *  Returns a list of execution IDs for active (not completed) executions
   */
  abstract fun getActiveExecutionIds(executionType: ExecutionType, partitionName: String?): List<String>

  /**
   * Returns a list of stage IDs that belong to the given executions
   */
  abstract fun getStageIdsForExecutions(executionType: ExecutionType, executionIds: List<String>): List<String>

  /**
   * Returns (a list of) full execution DB records with given execution IDs
   */
  abstract fun getExecutions(executionType: ExecutionType, ids: List<String>): Result<Record>

  /**
   * Returns (a list of) full stage DB records with given stage IDs
   */
  abstract fun getStages(executionType: ExecutionType, stageIds: List<String>): Result<Record>

  /**
   * Deletes specified stages
   */
  abstract fun deleteStages(executionType: ExecutionType, stageIdsToDelete: List<String>)

  /**
   * Delete specified executions
   */
  abstract fun deleteExecutions(executionType: ExecutionType, pipelineIdsToDelete: List<String>)

  /**
   * Load given records into the specified table using jooq loader api
   */
  abstract fun loadRecords(tableName: String, records: Result<Record>): Int

  data class ExecutionDiffKey(
    val id: String,
    val updated_at: Long
  )
}
