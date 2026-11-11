/*
 * Copyright 2026 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.config.SqlTaskLeaseProperties
import com.netflix.spinnaker.kork.sql.routing.withPool
import de.huxhorn.sulky.ulid.ULID
import java.util.concurrent.TimeUnit
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.slf4j.LoggerFactory

/** What the reaper does to the tasks of an instance whose lease has expired. */
interface TaskReaper {
  /**
   * Resolves the still-STARTED tasks owned by [owner] (whose registration epoch was
   * [epoch]): FAILED_RETRYABLE when the task carries a saga Orca can resume, FAILED
   * otherwise — a non-idempotent cloud operation is never re-run blindly.
   *
   * @return the number of tasks resolved
   */
  fun reapInstanceTasks(owner: String, epoch: Long): Int
}

/**
 * Finds clouddriver instances whose lease expired and resolves their orphaned tasks, so a
 * crashed, OOM-killed or partitioned pod's work gets an explicit outcome within about one
 * lease TTL instead of Orca waiting out its MonitorKatoTask timeout
 * (plans/2026/clouddriver-task-repository.md section 5a).
 *
 * Declaring an instance DEAD is a conditional update (`WHERE instance_id = ? AND epoch = ?
 * AND lease_expires_at < now`), so exactly one pod wins the reaping of any given instance.
 */
class SqlTaskReaperAgent(
  private val jooq: DSLContext,
  private val objectMapper: ObjectMapper,
  private val registry: Registry,
  private val properties: SqlTaskLeaseProperties,
  private val retries: SqlRetries = SqlRetries()
) : TaskReaper, RunnableAgent, CustomScheduledAgent {

  private val log = LoggerFactory.getLogger(javaClass)

  private val ulid = ULID()

  private val reapedInstanceId = registry.createId("sql.taskReaper.instancesReaped")
  private val reapedTaskId = registry.createId("sql.taskReaper.tasksReaped")
  private val timingId = registry.createId("sql.taskReaper.timing")

  override fun run() {
    withPool(ConnectionPools.TASKS.value) {
      val expired =
        jooq.read(retries) { j ->
          j.select(field("instance_id"), field("epoch"))
            .from(clouddriverInstancesTable)
            .where(
              field("state").ne(INSTANCE_DEAD)
                .and(field("lease_expires_at").lessThan(dbNowMillis(j)))
            )
            .fetch { r: org.jooq.Record ->
              Pair(
                r.get("instance_id", String::class.java),
                r.get("epoch", Long::class.javaObjectType)
              )
            }
        }

      for ((instanceId, epoch) in expired) {
        if (declareDead(instanceId, epoch)) {
          val resolved = reapInstanceTasks(instanceId, epoch)
          if (resolved > 0) {
            log.warn(
              "Instance {} (epoch {}) was declared dead with lease expired; resolved {} orphaned task(s)",
              instanceId,
              epoch,
              resolved
            )
          }
        }
      }
    }
  }

  /**
   * Conditional update: only the pod that still sees the expired lease wins, so a
   * partitioned pod that recovered and renewed its lease first is left alone.
   */
  private fun declareDead(instanceId: String, epoch: Long): Boolean {
    var won = false
    withPool(ConnectionPools.TASKS.value) {
      jooq.transactional(retries) { ctx ->
        won =
          ctx.update(clouddriverInstancesTable)
            .set(field("state"), INSTANCE_DEAD)
            .set(field("updated_at"), dbNowMillis(ctx))
            .where(
              field("instance_id").eq(instanceId)
                .and(field("epoch").eq(epoch))
                .and(field("state").ne(INSTANCE_DEAD))
                .and(field("lease_expires_at").lessThan(dbNowMillis(ctx)))
            )
            .execute() == 1
        if (won) {
          registry.counter(reapedInstanceId).increment()
        }
      }
    }
    return won
  }

  override fun reapInstanceTasks(owner: String, epoch: Long): Int {
    var resolved = 0
    withPool(ConnectionPools.TASKS.value) {
      val taskIds =
        jooq.read(retries) { j ->
          j.select(field("id"))
            .from(tasksTable)
            .where(
              field("owner_id").eq(owner)
                .and(field("lease_epoch").eq(epoch))
                .and(field("current_state").eq(TaskState.STARTED.toString()))
            )
            .fetch("id", String::class.java)
            .filterNotNull()
        }

      registry.timer(timingId).record {
        for (chunk in taskIds.chunked(properties.reaperBatchSize)) {
          for (taskId in chunk) {
            if (resolveOrphanedTask(taskId, owner, epoch)) {
              resolved++
            }
          }
        }
      }
      if (resolved > 0) {
        registry.counter(reapedTaskId).increment(resolved.toLong())
      }
    }
    return resolved
  }

  /**
   * Resolves one orphaned task under the task's row lock, re-checking that it is still
   * STARTED and still owned by the dead registration. Saga-backed tasks become
   * FAILED_RETRYABLE so Orca's existing resume path can pick them up; everything else is
   * FAILED — the operation may already have hit the cloud API, so it is never re-run.
   */
  private fun resolveOrphanedTask(taskId: String, owner: String, epoch: Long): Boolean {
    var resolved = false
    withPool(ConnectionPools.TASKS.value) {
      jooq.transactional(retries) { ctx ->
        val record =
          ctx.select(
                field("current_state"),
                field("next_seq"),
                field("lease_owner"),
                field("lease_epoch"),
                field("saga_ids")
              )
            .from(tasksTable)
            .where(field("id").eq(taskId))
            .forUpdate()
            .fetchOne()
            ?: return@transactional

        val currentState = record.get("current_state", String::class.java)
        val leaseOwner = record.get("lease_owner", String::class.java)
        val leaseEpoch = record.get("lease_epoch", Long::class.javaObjectType)
        if (currentState != TaskState.STARTED.toString() || leaseOwner != owner || leaseEpoch != epoch) {
          // The task finished, or the pod recovered and re-registered before we got here.
          return@transactional
        }

        val sagaIds =
          try {
            record.get("saga_ids", String::class.java)?.let { s ->
              objectMapper.readValue(s, List::class.java)
            }
          } catch (e: Exception) {
            log.warn("Could not parse saga ids of task {}: {}", taskId, e.message)
            null
          }
        val sagaResumable = !sagaIds.isNullOrEmpty()
        val state = if (sagaResumable) TaskState.FAILED_RETRYABLE else TaskState.FAILED
        val message =
          if (sagaResumable) {
            "clouddriver instance executing this task stopped before it completed; the saga can be resumed"
          } else {
            "clouddriver instance executing this task stopped before it completed"
          }

        val nextSeq = (record.get("next_seq", Long::class.javaObjectType) ?: FIRST_SEQ)
        ctx.insertInto(
              taskStatesTable,
              field("id"),
              field("task_id"),
              field("created_at"),
              field("seq"),
              field("state"),
              field("phase"),
              field("status")
            )
            .values(
              ulid.nextULID(),
              taskId,
              System.currentTimeMillis(),
              nextSeq,
              state.toString(),
              "ORCHESTRATION",
              message.take(MAX_STATUS_LENGTH)
            )
            .execute()
        ctx.update(tasksTable)
          .set(field("next_seq"), nextSeq + 1)
          .set(field("current_state"), state.toString())
          .set(field("completed_at"), System.currentTimeMillis())
          .set(field("lease_owner"), owner)
          .set(field("lease_epoch"), epoch)
          .where(field("id").eq(taskId))
          .execute()
        resolved = true
      }
    }
    return resolved
  }

  override fun getAgentType(): String = javaClass.simpleName
  override fun getProviderName(): String = CoreProvider.PROVIDER_NAME
  override fun getPollIntervalMillis(): Long = DEFAULT_POLL_INTERVAL_MILLIS
  override fun getTimeoutMillis(): Long = DEFAULT_TIMEOUT_MILLIS

  companion object {
    private val DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1)
    private val DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(1)
    private const val FIRST_SEQ = 1L
    private const val MAX_STATUS_LENGTH = 200
  }
}
