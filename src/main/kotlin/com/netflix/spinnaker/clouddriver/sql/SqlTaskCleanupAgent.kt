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
package com.netflix.spinnaker.clouddriver.sql

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.clouddriver.data.task.TaskState.COMPLETED
import com.netflix.spinnaker.clouddriver.data.task.TaskState.FAILED
import com.netflix.spinnaker.config.SqlTaskCleanupAgentProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.Arrays
import java.util.concurrent.TimeUnit

/**
 * Cleans up completed Tasks after a configurable TTL.
 */
class SqlTaskCleanupAgent(
  private val jooq: DSLContext,
  private val clock: Clock,
  private val registry: Registry,
  private val properties: SqlTaskCleanupAgentProperties,
  private val sqlRetryProperties: SqlRetryProperties
) : RunnableAgent, CustomScheduledAgent {

  private val log = LoggerFactory.getLogger(javaClass)

  private val deletedId = registry.createId("sql.taskCleanupAgent.deleted")
  private val timingId = registry.createId("sql.taskCleanupAgent.timing")

  override fun run() {
    val candidates = jooq.withRetry(sqlRetryProperties.reads) {
      val candidates = it.select(field("id"), field("task_id"))
        .from(taskStatesTable)
        .where(
          field("state").`in`(COMPLETED.toString(), FAILED.toString())
            .and(field("created_at").greaterOrEqual(
              clock.instant().minusMillis(properties.completedTtlMs).toEpochMilli())
            )
        )
        .fetch()

      val candidateTaskIds = candidates.map { r -> r.field("task_id").getValue(r) }.filterNotNull().toTypedArray()
      val candidateTaskStateIds = candidates.map { r -> r.field("id").getValue(r) }.filterNotNull().toTypedArray()

      val candidateResultIds =
        if (candidateTaskIds.isNotEmpty()) {
          it.select(field("id"))
            .from(taskResultsTable)
            .where(field("task_id").`in`(candidateTaskIds))
            .fetch("id")
            .filterNotNull()
            .toTypedArray()
        } else {
          emptyArray()
        }

      CleanupCandidateIds(
        taskIds = candidateTaskIds,
        stateIds = candidateTaskStateIds,
        resultIds = candidateResultIds
      )

    }

    if (candidates.hasAny()) {
      log.info(
        "Cleaning up {} completed tasks ({} states, {} result objects)",
        candidates.taskIds.size,
        candidates.stateIds.size,
        candidates.resultIds.size
      )

      registry.timer(timingId).record {
        jooq.transactional(sqlRetryProperties.transactions) {
          jooq.deleteFrom(taskResultsTable).where(field("id").`in`(candidates.resultIds))
          jooq.deleteFrom(taskStatesTable).where(field("id").`in`(candidates.stateIds))
          jooq.deleteFrom(tasksTable).where(field("id").`in`(candidates.taskIds))
        }
      }
      registry.counter(deletedId).increment(candidates.taskIds.size.toLong())
    }
  }

  override fun getAgentType(): String = javaClass.simpleName
  override fun getProviderName(): String = CoreProvider.PROVIDER_NAME
  override fun getPollIntervalMillis(): Long = DEFAULT_POLL_INTERVAL_MILLIS
  override fun getTimeoutMillis(): Long = DEFAULT_TIMEOUT_MILLIS

  companion object {
    private val DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5)
    private val DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3)
  }
}

private data class CleanupCandidateIds(
  val taskIds: Array<Any>,
  val stateIds: Array<Any>,
  val resultIds: Array<Any>
) {
  fun hasAny() = taskIds.isNotEmpty()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CleanupCandidateIds

    if (!Arrays.equals(taskIds, other.taskIds)) return false
    if (!Arrays.equals(stateIds, other.stateIds)) return false
    if (!Arrays.equals(resultIds, other.resultIds)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = Arrays.hashCode(taskIds)
    result = 31 * result + Arrays.hashCode(stateIds)
    result = 31 * result + Arrays.hashCode(resultIds)
    return result
  }
}
