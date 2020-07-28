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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

open class ExecutionCopier(
  /**
   * ID of our peer
   */
  private val peeredId: String,

  /**
   * Source (our peers) database access layer
   */
  private val srcDB: SqlRawAccess,

  /**
   * Destination (our own) database access layer
   */
  private val destDB: SqlRawAccess,

  /**
   * Executor service to use for scheduling parallel copying
   */
  private val executor: ExecutorService,

  /**
   * Number of parallel threads to use for copying
   * (it's expected that the executor is configured with this many as well)
   */
  private val threadCount: Int,

  /**
   * Chunk size to use during copy (this translates to how many IDs we mutate in a single DB query)
   */
  private val chunkSize: Int,

  /**
   * Metrics abstraction
   */
  private val peeringMetrics: PeeringMetrics
) {

  private val log = LoggerFactory.getLogger(javaClass)

  /**
   * Run copy in parallel
   * Chunks the specified IDs and uses the specified action to perform migrations on separate threads
   */
  open fun copyInParallel(executionType: ExecutionType, idsToMigrate: List<String>, state: ExecutionState): MigrationChunkResult {
    val queue = ConcurrentLinkedQueue(idsToMigrate.chunked(chunkSize))

    val startTime = Instant.now()
    val migratedCount = AtomicInteger(0)

    // Only spin up as many threads as there are chunks to migrate (with upper bound of threadCount)
    val effectiveThreadCount = min(threadCount, queue.size)
    log.info("Kicking off migration with (chunk size: $chunkSize, threadCount: $effectiveThreadCount)")

    val futures = MutableList<Future<MigrationChunkResult>>(effectiveThreadCount) { threadIndex ->
      executor.submit(
        Callable<MigrationChunkResult> {
          var latestUpdatedAt = 0L
          var hadErrors = false

          do {
            val chunkToProcess = queue.poll() ?: break

            val result = copyExecutionChunk(executionType, chunkToProcess, state)
            hadErrors = hadErrors || result.hadErrors
            val migrated = migratedCount.addAndGet(result.count)
            latestUpdatedAt = max(latestUpdatedAt, result.latestUpdatedAt)

            if (threadIndex == 0) {
              // Only dump status logs for one of the threads - it's informational only anyway
              val elapsedTime = Duration.between(startTime, Instant.now()).toMillis()
              val etaMillis = (((idsToMigrate.size.toDouble() / migrated) * elapsedTime) - elapsedTime).toLong()
              log.info("Migrated $migrated of ${idsToMigrate.size}, ETA: ${Duration.ofMillis(etaMillis)}")
            }
          } while (true)

          return@Callable MigrationChunkResult(latestUpdatedAt, 0, hadErrors)
        }
      )
    }

    var latestUpdatedAt = 0L
    var hadErrors = false
    for (future in futures) {
      val singleResult = future.get()
      hadErrors = hadErrors || singleResult.hadErrors
      latestUpdatedAt = max(latestUpdatedAt, singleResult.latestUpdatedAt)
    }

    return MigrationChunkResult(latestUpdatedAt, migratedCount.get(), hadErrors)
  }

  /**
   * Copies executions (orchestrations or pipelines) and its stages given IDs of the executions
   */
  private fun copyExecutionChunk(executionType: ExecutionType, idsToMigrate: List<String>, state: ExecutionState): MigrationChunkResult {
    var latestUpdatedAt = 0L
    try {
      // Step 0: Capture the source data for executions and their stages
      // NOTE: it's important that we capture the executions BEFORE we capture their stages
      // The reason is that we key our diff off the updated_at timestamp of the execution.
      // We can't have the execution update in the time we capture its stages and the time we capture the execution it self
      // It's totally fine for the stages to be "newer" than the execution since that will be fixed up in the next agent run
      val executionRows = srcDB.getExecutions(executionType, idsToMigrate)
      val stagesInSource = srcDB.getStageIdsForExecutions(executionType, idsToMigrate)
      val stagesInSourceHash = stagesInSource.map { it.id }.toHashSet()

      // Step 1: Copy over stages before the executions themselves -
      // if we saved executions first the user could request an execution but it wouldn't have any stages yet

      // It is possible that the source stage list has mutated. Normally, this is only possible when an execution
      // is restarted (e.g. restarting a deploy stage will delete all its synthetic stages and start over).
      // We delete all stages that are no longer in our peer first, then we update/copy all other stages
      val stagesInDest = destDB.getStageIdsForExecutions(executionType, idsToMigrate)
      val stagesInDestMap = stagesInDest.map { it.id to it }.toMap()

      val stageIdsToDelete = stagesInDest.filter { !stagesInSourceHash.contains(it.id) }.map { it.id }
      if (stageIdsToDelete.any()) {
        destDB.deleteStages(executionType, stageIdsToDelete)
        peeringMetrics.incrementNumStagesDeleted(executionType, stageIdsToDelete.size)
      }

      val stageIdsToMigrate = stagesInSource
        .filter { key -> stagesInDestMap[key.id]?.updated_at ?: 0 < key.updated_at }
        .map { it.id }

      for (chunk in stageIdsToMigrate.chunked(chunkSize)) {
        val stageRows = srcDB.getStages(executionType, chunk)
        destDB.loadRecords(getStagesTable(executionType).name, stageRows)
      }

      // Step 2: Copy all executions
      executionRows.forEach { r ->
        r.set(DSL.field(DSL.name("partition")), peeredId)
        latestUpdatedAt = max(latestUpdatedAt, r.get("updated_at", Long::class.java))
      }
      destDB.loadRecords(getExecutionTable(executionType).name, executionRows)
      peeringMetrics.incrementNumPeered(executionType, state, idsToMigrate.size)

      return MigrationChunkResult(latestUpdatedAt, idsToMigrate.size, hadErrors = false)
    } catch (e: Exception) {
      log.error("Failed to peer $executionType chunk (first id: ${idsToMigrate[0]})", e)

      peeringMetrics.incrementNumErrors(executionType)
    }

    return MigrationChunkResult(0, 0, hadErrors = true)
  }

  data class MigrationChunkResult(
    val latestUpdatedAt: Long,
    val count: Int,
    val hadErrors: Boolean = false
  )
}
