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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskOutput
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import com.netflix.spinnaker.clouddriver.data.task.TaskState.STARTED
import com.netflix.spinnaker.kork.sql.routing.withPool
import de.huxhorn.sulky.ulid.ULID
import java.time.Clock
import java.time.Duration
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.exception.SQLStateClass
import org.jooq.Select
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.noCondition
import org.jooq.impl.DSL.sql
import org.slf4j.LoggerFactory

/**
 * Task state is ordered by a per-task sequence number (`seq`), assigned while holding a row lock on the
 * task, so the order of a task's history never depends on pod clocks or on how IDs sort. `tasks.current_state`
 * holds the latest state, so finding running tasks never has to aggregate `task_states`.
 *
 * Tasks created before those columns existed have NULL `seq`/`current_state`. Their rows sort first, in the
 * old `(created_at, id)` order, and their `current_state` is filled in by their next write.
 */
class SqlTaskRepository(
  private val jooq: DSLContext,
  private val mapper: ObjectMapper,
  private val clock: Clock,
  private val poolName: String
) : TaskRepository {

  private val log = LoggerFactory.getLogger(javaClass)

  init {
    log.info("Using ${javaClass.simpleName} with pool $poolName")
  }

  override fun create(phase: String, status: String): Task {
    return create(phase, status, ulid.nextULID())
  }

  override fun create(phase: String, status: String, clientRequestId: String): Task {
    // A task already exists for this request: return it untouched.
    getByClientRequestId(clientRequestId)?.let { return it }

    val task = SqlTask(ulid.nextULID(), ClouddriverHostname.ID, clientRequestId, clock.millis(), mutableSetOf(), this)
    try {
      withPool(poolName) {
        jooq.transactional { ctx ->
          val pairs = mapOf(
            field("id") to task.id,
            field("owner_id") to task.ownerId,
            field("request_id") to task.requestId,
            field("created_at") to task.startTimeMs,
            field("saga_ids") to mapper.writeValueAsString(task.sagaIds),
            field("current_state") to STARTED.toString(),
            field("next_seq") to FIRST_SEQ + 1
          )
          ctx.insertInto(tasksTable, *pairs.keys.toTypedArray()).values(*pairs.values.toTypedArray()).execute()
          insertState(ctx, task.id, FIRST_SEQ, STARTED, phase, status)
        }
      }
    } catch (e: DataAccessException) {
      if (e.sqlStateClass() != SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION) {
        throw e
      }
      // A concurrent request with the same client request ID created its task first (request_id is unique).
      return getByClientRequestId(clientRequestId) ?: throw e
    }

    // TODO(rz): So janky and bad.
    task.refresh(true)
    return task
  }

  fun updateSagaIds(task: Task) {
    return withPool(poolName) {
      jooq.transactional { ctx ->
        ctx.update(tasksTable)
          .set(field("saga_ids"), mapper.writeValueAsString(task.sagaIds))
          .where(field("id").eq(task.id))
          .execute()
      }
    }
  }

  override fun get(id: String): Task? {
    return retrieveInternal(id)
  }

  override fun getByClientRequestId(clientRequestId: String): Task? {
    return withPool(poolName) {
      jooq.read {
        it.select(field("id"))
          .from(tasksTable)
          .where(field("request_id").eq(clientRequestId))
          .fetchOne("id", String::class.java)
          ?.let { taskId ->
            retrieveInternal(taskId)
          }
      }
    }
  }

  override fun list(): MutableList<Task> {
    return withPool(poolName) {
      jooq.read {
        runningTaskIds(it, false).let { taskIds ->
          retrieveInternal(field("id").`in`(*taskIds), field("task_id").`in`(*taskIds)).toMutableList()
        }
      }
    }
  }

  override fun listByThisInstance(): MutableList<Task> {
    return withPool(poolName) {
      jooq.read {
        runningTaskIds(it, true).let { taskIds ->
          retrieveInternal(field("id").`in`(*taskIds), field("task_id").`in`(*taskIds)).toMutableList()
        }
      }
    }
  }

  internal fun addResultObjects(results: List<Any>, task: Task) {
    withPool(poolName) {
      jooq.transactional { ctx ->
        val row = lockTask(ctx, task.id)
        currentStatus(ctx, task.id, row).ensureUpdateable()

        results.forEachIndexed { index, result ->
          ctx.insertInto(taskResultsTable, listOf(field("id"), field("task_id"), field("seq"), field("body")))
            .values(listOf(ulid.nextULID(), task.id, row.nextSeq + index, mapper.writeValueAsString(result)))
            .execute()
        }
        updateTaskRow(ctx, task.id, row.nextSeq + results.size)
      }
    }
  }

  internal fun updateCurrentStatus(task: Task, phase: String, status: String) {
    withPool(poolName) {
      jooq.transactional { ctx ->
        val row = lockTask(ctx, task.id)
        // Throws if the task has already reached a terminal state.
        val updated = currentStatus(ctx, task.id, row).update(phase, status.take(MAX_STATUS_LENGTH))
        insertState(ctx, task.id, row.nextSeq, updated.state, updated.phase, updated.status)
        updateTaskRow(ctx, task.id, row.nextSeq + 1, updated.state)
      }
    }
  }

  internal fun updateState(task: Task, state: TaskState) {
    withPool(poolName) {
      jooq.transactional { ctx ->
        val row = lockTask(ctx, task.id)
        // Throws unless the transition is allowed: nothing leaves a terminal state except retrying a
        // FAILED_RETRYABLE task.
        val updated = currentStatus(ctx, task.id, row).update(state)
        insertState(ctx, task.id, row.nextSeq, updated.state, updated.phase, updated.status)
        updateTaskRow(ctx, task.id, row.nextSeq + 1, updated.state)
      }
    }
  }

  internal fun updateOutput(taskOutput: TaskOutput, task: Task) {
    withPool(poolName) {
      jooq.transactional { ctx ->
        val row = lockTask(ctx, task.id)
        ctx
          .insertInto(
            taskOutputsTable,
            listOf(
              field("id"),
              field("task_id"),
              field("seq"),
              field("created_at"),
              field("manifest"),
              field("phase"),
              field("std_out"),
              field("std_error")
            )
          )
          .values(
            listOf(
              ulid.nextULID(),
              task.id,
              row.nextSeq,
              clock.millis(),
              taskOutput.manifest,
              taskOutput.phase,
              taskOutput.stdOut,
              taskOutput.stdError
            )
          )
          .execute()
        updateTaskRow(ctx, task.id, row.nextSeq + 1)
      }
    }
  }

  fun updateOwnerId(task: Task) {
    return withPool(poolName) {
      jooq.transactional { ctx ->
        ctx.update(tasksTable)
          .set(field("owner_id"), task.ownerId)
          .where(field("id").eq(task.id))
          .execute()
      }
    }
  }

  internal fun retrieveInternal(taskId: String): Task? {
    return retrieveInternal(field("id").eq(taskId), field("task_id").eq(taskId)).firstOrNull()
  }

  private fun retrieveInternal(condition: Condition, relationshipCondition: Condition? = null): Collection<Task> {
    val tasks = mutableSetOf<Task>()

    // TODO: AWS Aurora enforces REPEATABLE_READ on replicas. Kork's dataSourceConnectionProvider sets READ_COMMITTED
    //  on every connection acquire - need to change this so running on !aurora will behave consistently.
    //  REPEATABLE_READ is correct here.
    withPool(poolName) {
      jooq.transactional { ctx ->
        // One UNION ALL across the task and its states, results and outputs. Each child row carries its ordering
        // columns (seq, sort_created_at, row_id); TaskMapper sorts each task's rows by them.
        tasks.addAll(
          ctx
            .select(
              field("id").`as`("task_id"),
              field("owner_id"),
              field("request_id"),
              field("created_at"),
              field("saga_ids"),
              field(sql("null")).`as`("body"),
              field(sql("null")).`as`("state"),
              field(sql("null")).`as`("phase"),
              field(sql("null")).`as`("status"),
              field(sql("null")).`as`("manifest"),
              field(sql("null")).`as`("std_out"),
              field(sql("null")).`as`("std_error"),
              field(sql("null")).`as`("seq"),
              field(sql("null")).`as`("sort_created_at"),
              field(sql("null")).`as`("row_id")
            )
            .from(tasksTable)
            .where(condition)
            .unionAll(
              ctx
                .select(
                  field("task_id"),
                  field(sql("null")).`as`("owner_id"),
                  field(sql("null")).`as`("request_id"),
                  field(sql("null")).`as`("created_at"),
                  field(sql("null")).`as`("saga_ids"),
                  field(sql("null")).`as`("body"),
                  field("state"),
                  field("phase"),
                  field("status"),
                  field(sql("null")).`as`("manifest"),
                  field(sql("null")).`as`("std_out"),
                  field(sql("null")).`as`("std_error"),
                  field("seq"),
                  field("created_at").`as`("sort_created_at"),
                  field("id").`as`("row_id")
                )
                .from(taskStatesTable)
                .where(relationshipCondition ?: condition)
            )
            .unionAll(
              ctx
                .select(
                  field("task_id"),
                  field(sql("null")).`as`("owner_id"),
                  field(sql("null")).`as`("request_id"),
                  field(sql("null")).`as`("created_at"),
                  field(sql("null")).`as`("saga_ids"),
                  field("body"),
                  field(sql("null")).`as`("state"),
                  field(sql("null")).`as`("phase"),
                  field(sql("null")).`as`("status"),
                  field(sql("null")).`as`("manifest"),
                  field(sql("null")).`as`("std_out"),
                  field(sql("null")).`as`("std_error"),
                  field("seq"),
                  field(sql("null")).`as`("sort_created_at"),
                  field("id").`as`("row_id")
                )
                .from(taskResultsTable)
                .where(relationshipCondition ?: condition)
            )
            .unionAll(
              ctx
                .select(
                  field("task_id"),
                  field(sql("null")).`as`("owner_id"),
                  field(sql("null")).`as`("request_id"),
                  field(sql("null")).`as`("created_at"),
                  field(sql("null")).`as`("saga_ids"),
                  field(sql("null")).`as`("body"),
                  field(sql("null")).`as`("state"),
                  field("phase"),
                  field(sql("null")).`as`("status"),
                  field("manifest"),
                  field("std_out"),
                  field("std_error"),
                  field("seq"),
                  field("created_at").`as`("sort_created_at"),
                  field("id").`as`("row_id")
                )
                .from(taskOutputsTable)
                .where(relationshipCondition ?: condition)
            )
            .fetchTasks()
        )
      }
    }

    return tasks
  }

  private data class TaskRow(val currentState: TaskState?, val nextSeq: Long)

  /**
   * Locks the task's row for the rest of the transaction, so writes to one task are serialized and each gets
   * the next sequence number.
   */
  private fun lockTask(ctx: DSLContext, taskId: String): TaskRow {
    val record = ctx.select(field("current_state"), field("next_seq"))
      .from(tasksTable)
      .where(field("id").eq(taskId))
      .forUpdate()
      .fetchOne()
      ?: throw IllegalStateException("Task $taskId does not exist")
    return TaskRow(
      record.get("current_state", String::class.java)?.let { TaskState.valueOf(it) },
      record.get("next_seq", Long::class.javaObjectType) ?: FIRST_SEQ
    )
  }

  /** The task's latest status. Its state comes from `tasks.current_state` when that's set. */
  private fun currentStatus(ctx: DSLContext, taskId: String, row: TaskRow): DefaultTaskStatus {
    val latest = ctx.select(taskStatesFields)
      .from(taskStatesTable)
      .where(field("task_id").eq(taskId))
      .orderBy(field("seq").desc().nullsLast(), field("created_at").desc(), field("id").desc())
      .limit(1)
      .fetchTaskStatus()
      ?: throw IllegalStateException("Task $taskId has no state")
    return row.currentState
      ?.let { DefaultTaskStatus.create(latest.phase, latest.status, it) }
      ?: latest
  }

  private fun insertState(ctx: DSLContext, taskId: String, seq: Long, state: TaskState, phase: String, status: String) {
    ctx
      .insertInto(
        taskStatesTable,
        listOf(field("id"), field("task_id"), field("seq"), field("created_at"), field("state"), field("phase"), field("status"))
      )
      .values(listOf(ulid.nextULID(), taskId, seq, clock.millis(), state.toString(), phase, status))
      .execute()
  }

  /** Advances the task's sequence and, when [state] is given, records it as the task's current state. */
  private fun updateTaskRow(ctx: DSLContext, taskId: String, nextSeq: Long, state: TaskState? = null) {
    var update = ctx.update(tasksTable).set(field("next_seq"), nextSeq)
    if (state != null) {
      update = update
        .set(field("current_state"), state.toString())
        .set(field("completed_at"), if (state.isCompleted) clock.millis() else null)
    }
    update.where(field("id").eq(taskId)).execute()
  }

  /**
   * IDs of tasks whose current state is STARTED.
   *
   * Tasks created before `current_state` existed have it NULL until their next write. Those are checked
   * against their latest history row instead, but only if created within [LEGACY_RUNNING_WINDOW]: older
   * pre-upgrade tasks can't still be running, and the bound keeps this query cheap once all such tasks have
   * aged out.
   */
  private fun runningTaskIds(ctx: DSLContext, thisInstance: Boolean): Array<String> {
    return withPool(poolName) {
      val owner = if (thisInstance) field("owner_id").eq(ClouddriverHostname.ID) else noCondition()
      val current = ctx.select(field("id"))
        .from(tasksTable)
        .where(field("current_state").eq(STARTED.toString()).and(owner))
        .fetch("id", String::class.java)

      val legacyLatestState = ctx.select(field("s.state"))
        .from(taskStatesTable.`as`("s"))
        .where(field("s.task_id").eq(field("t.id")))
        .orderBy(field("s.created_at").desc(), field("s.id").desc())
        .limit(1)
      val legacyOwner = if (thisInstance) field("t.owner_id").eq(ClouddriverHostname.ID) else noCondition()
      val legacy = ctx.select(field("t.id"))
        .from(tasksTable.`as`("t"))
        .where(
          field("t.current_state").isNull
            .and(field("t.created_at").gt(clock.millis() - LEGACY_RUNNING_WINDOW.toMillis()))
            .and(legacyLatestState.asField<String>().eq(STARTED.toString()))
            .and(legacyOwner)
        )
        .fetch("t.id", String::class.java)

      (current + legacy).distinct().toTypedArray()
    }
  }

  private fun Select<out Record>.fetchTasks() =
    TaskMapper(this@SqlTaskRepository, mapper).map(fetch().intoResultSet())

  private fun Select<out Record>.fetchTaskStatuses() =
    TaskStatusMapper().map(fetch().intoResultSet())

  private fun Select<out Record>.fetchTaskStatus() =
    fetchTaskStatuses().firstOrNull()

  companion object {
    private val ulid = ULID()
    private val MAX_STATUS_LENGTH = 10_000
    private const val FIRST_SEQ = 1L
    private val LEGACY_RUNNING_WINDOW = Duration.ofDays(1)
  }
}
