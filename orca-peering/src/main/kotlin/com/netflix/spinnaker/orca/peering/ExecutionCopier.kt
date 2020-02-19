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

import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

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
  open fun copyInParallel(executionType: Execution.ExecutionType, idsToMigrate: List<String>, state: ExecutionState): MigrationChunkResult {
    val queue = ConcurrentLinkedQueue(idsToMigrate.chunked(chunkSize))

    val startTime = Instant.now()
    val migratedCount = AtomicInteger(0)

    // Only spin up as many threads as there are chunks to migrate (with upper bound of threadCount)
    val effectiveThreadCount = min(threadCount, queue.size)
    log.info("Kicking off migration with (chunk size: $chunkSize, threadCount: $effectiveThreadCount)")

    val futures = MutableList<Future<MigrationChunkResult>>(effectiveThreadCount) { threadIndex ->
      executor.submit(Callable<MigrationChunkResult> {
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
      })
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
  private fun copyExecutionChunk(executionType: Execution.ExecutionType, idsToMigrate: List<String>, state: ExecutionState): MigrationChunkResult {
    var latestUpdatedAt = 0L
    try {
      // Step 1: Copy all stages
      val stagesToMigrate = srcDB.getStageIdsForExecutions(executionType, idsToMigrate)

      // It is possible that the source stage list has mutated. Normally, this is only possible when an execution
      // is restarted (e.g. restarting a deploy stage will delete all its synthetic stages and start over).
      // We delete all stages that are no longer in our peer first, then we update/copy all other stages
      val stagesPresent = destDB.getStageIdsForExecutions(executionType, idsToMigrate)
      val stagesToMigrateHash = stagesToMigrate.toHashSet()
      val stagesToDelete = stagesPresent.filter { !stagesToMigrateHash.contains(it) }
      if (stagesToDelete.any()) {
        destDB.deleteStages(executionType, stagesToDelete)
        peeringMetrics.incrementNumStagesDeleted(executionType, stagesToDelete.size)
      }

      for (chunk in stagesToMigrate.chunked(chunkSize)) {
        val rows = srcDB.getStages(executionType, chunk)
        destDB.loadRecords(getStagesTable(executionType).name, rows)
      }

      // Step 2: Copy all executions
      val rows = srcDB.getExecutions(executionType, idsToMigrate)
      rows.forEach { r -> r.set(DSL.field("partition"), peeredId)
        latestUpdatedAt = max(latestUpdatedAt, r.get("updated_at", Long::class.java))
      }
      destDB.loadRecords(getExecutionTable(executionType).name, rows)
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
