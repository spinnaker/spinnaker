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
 *
 */
package com.netflix.spinnaker.clouddriver.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import com.netflix.spinnaker.config.SqlTaskLeaseProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.jooq.impl.DSL.field
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Instant
import java.time.temporal.ChronoUnit

class SqlTaskReaperAgentTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after { SqlTestUtil.cleanupDb(database.context) }

    test("an expired instance is declared dead exactly once") {
      reaper.run()
      expectThat(instanceState(DEAD_INSTANCE)).isEqualTo(INSTANCE_DEAD)

      // A second pass must not touch it again (already DEAD).
      reaper.run()
      expectThat(instanceState(DEAD_INSTANCE)).isEqualTo(INSTANCE_DEAD)
    }

    test("a live instance with an unexpired lease is left alone") {
      reaper.run()
      expectThat(instanceState(LIVE_INSTANCE)).isEqualTo(INSTANCE_ACTIVE)
    }

    test("orphaned tasks of a dead instance are resolved") {
      reaper.run()

      // No saga recorded: FAILED, never re-run blindly.
      expectThat(taskState(TASK_NO_SAGA)).isEqualTo(TaskState.FAILED.toString())
      expectThat(taskStatus(TASK_NO_SAGA)).isEqualTo(
        "clouddriver instance executing this task stopped before it completed"
      )

      // Saga-backed: FAILED_RETRYABLE, so Orca's resume path can pick it up.
      expectThat(taskState(TASK_WITH_SAGA)).isEqualTo(TaskState.FAILED_RETRYABLE.toString())

      // Already-terminal tasks are untouched.
      expectThat(taskState(TASK_DONE)).isEqualTo(TaskState.COMPLETED.toString())

      // Tasks of a live instance are untouched.
      expectThat(taskState(TASK_LIVE_OWNER)).isEqualTo(TaskState.STARTED.toString())
    }

    test("the reaped tasks carry a history entry with the reaper's message") {
      reaper.run()
      val states =
        database.context
          .select(field("state"))
          .from(taskStatesTable)
          .where(field("task_id").eq(TASK_NO_SAGA))
          .fetch(field("state"), String::class.java)
      expectThat(states).isEqualTo(listOf(TaskState.STARTED.toString(), TaskState.FAILED.toString()))
    }
  }

  inner class Fixture {
    val database = SqlTestUtil.initTcMysqlDatabase()!!
    val properties = SqlTaskLeaseProperties()
    val reaper =
      SqlTaskReaperAgent(database.context, ObjectMapper(), NoopRegistry(), properties)

    val DEAD_INSTANCE = "dead-instance"
    val LIVE_INSTANCE = "live-instance"
    val TASK_NO_SAGA = "task-no-saga"
    val TASK_WITH_SAGA = "task-with-saga"
    val TASK_DONE = "task-done"
    val TASK_LIVE_OWNER = "task-live-owner"

    fun seedInstance(id: String, expired: Boolean, epoch: Long = 0L) {
      val now = Instant.now()
      val leaseExpiry = if (expired) now.minus(1, ChronoUnit.MINUTES) else now.plus(2, ChronoUnit.MINUTES)
      database.context
        .insertInto(
          clouddriverInstancesTable,
          field("instance_id"),
          field("epoch"),
          field("state"),
          field("lease_expires_at"),
          field("updated_at")
        )
        .values(id, epoch, INSTANCE_ACTIVE, leaseExpiry.toEpochMilli(), now.toEpochMilli())
        .execute()
    }

    fun seedTask(id: String, owner: String, epoch: Long, sagaIds: List<String>) {
      val now = Instant.now().toEpochMilli()
      database.context
        .insertInto(
          tasksTable,
          field("id"),
          field("request_id"),
          field("owner_id"),
          field("created_at"),
          field("saga_ids"),
          field("current_state"),
          field("next_seq"),
          field("lease_owner"),
          field("lease_epoch")
        )
        .values(
          id,
          "$id-request",
          owner,
          now,
          ObjectMapper().writeValueAsString(sagaIds),
          TaskState.STARTED.toString(),
          2L,
          owner,
          epoch
        )
        .execute()
      database.context
        .insertInto(
          taskStatesTable,
          field("id"),
          field("task_id"),
          field("created_at"),
          field("seq"),
          field("state"),
          field("phase"),
          field("status")
        )
        .values("s-$id", id, now, 1L, TaskState.STARTED.toString(), "ORCHESTRATION", "started")
        .execute()
    }

    init {
      seedInstance(DEAD_INSTANCE, expired = true)
      seedInstance(LIVE_INSTANCE, expired = false)
      seedTask(TASK_NO_SAGA, DEAD_INSTANCE, 0L, emptyList())
      seedTask(TASK_WITH_SAGA, DEAD_INSTANCE, 0L, listOf("saga-1"))
      seedTask(TASK_DONE, DEAD_INSTANCE, 0L, emptyList()).also {
        database.context
          .update(tasksTable)
          .set(field("current_state"), TaskState.COMPLETED.toString())
          .where(field("id").eq(TASK_DONE))
          .execute()
      }
      seedTask(TASK_LIVE_OWNER, LIVE_INSTANCE, 0L, emptyList())
    }

    fun instanceState(id: String): String =
      database.context
        .select(field("state"))
        .from(clouddriverInstancesTable)
        .where(field("instance_id").eq(id))
        .fetchOne(field("state"), String::class.java)!!

    fun taskState(id: String): String =
      database.context
        .select(field("current_state"))
        .from(tasksTable)
        .where(field("id").eq(id))
        .fetchOne(field("current_state"), String::class.java)!!

    fun taskStatus(id: String): String =
      database.context
        .select(field("status"))
        .from(taskStatesTable)
        .where(field("task_id").eq(id))
        .orderBy(field("seq").desc())
        .limit(1)
        .fetchOne(field("status"), String::class.java)!!
  }
}
