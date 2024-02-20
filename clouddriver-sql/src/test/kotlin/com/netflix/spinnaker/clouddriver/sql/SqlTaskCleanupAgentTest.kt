/*
 * Copyright 2021 Salesforce, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.sql.event.SqlEventCleanupAgent
import com.netflix.spinnaker.config.SqlEventCleanupAgentConfigProperties
import com.netflix.spinnaker.config.SqlTaskCleanupAgentProperties
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit


class SqlTaskCleanupAgentTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    before {

      listOf(
        Instant.now().minus(3, ChronoUnit.DAYS),
        Instant.now().minus(10, ChronoUnit.DAYS),
      ).forEachIndexed { i, ts ->
        database.context
          .insertInto(tasksTable)
          .values(
            "myid$i",
            "7b96fe8de1e5e8e8620036480771195b8e25c583c9f4f0098a23e97bf2ba013b",
            "95637b33-6699-4abf-b1ab-d4077e1cf867@spin-clouddriver-7847bc646b-hgkfd",
            ts.toEpochMilli(),
            objectMapper.writeValueAsString(mutableListOf<String>())
          )
          .execute()

        database.context
          .insertInto(taskResultsTable)
          .values(
            "$i",
            "myid$i",
            "body"
          )
          .execute()

        database.context
          .insertInto(taskStatesTable)
          .values(
            "$i",
            "myid$i",
            ts.toEpochMilli(),
            "COMPLETED",
            "ORCHESTRATION",
            "Orchestration completed"
          )
          .execute()

        database.context
          .insertInto(taskOutputsTable)
          .values(
            "$i",
            "myid$i",
            ts.toEpochMilli(),
            "configMap render-helm-output-manifest-test-v000",
            "DEPLOY_KUBERNETES_MANIFEST",
            "stOut",
            "stdError"
          )
          .execute()
      }
    }

    after {
      SqlTestUtil.cleanupDb(database.context)
    }

    test("deletes old tasks and related data") {
      expectThat(database.context.fetchCount(tasksTable)).isEqualTo(2)
      expectThat(database.context.fetchCount(taskStatesTable)).isEqualTo(2)
      expectThat(database.context.fetchCount(taskResultsTable)).isEqualTo(2)
      expectThat(database.context.fetchCount(taskOutputsTable)).isEqualTo(2)
      subject.run()

      expectThat(database.context.fetchCount(tasksTable)).isEqualTo(1)
      expectThat(database.context.fetchCount(taskStatesTable)).isEqualTo(1)
      expectThat(database.context.fetchCount(taskResultsTable)).isEqualTo(1)
      expectThat(database.context.fetchCount(taskOutputsTable)).isEqualTo(1)

      val tasksResultset = database.context.select()
        .from(tasksTable)
        .fetch("id", String::class.java)
        .toTypedArray()

      expectThat(tasksResultset.size).isEqualTo(1)
      expectThat(tasksResultset.get(0)).isEqualTo("myid0")

      val taskResultsResultset = database.context.select()
        .from(taskResultsTable)
        .fetch("task_id", String::class.java)
        .toTypedArray()

      expectThat(taskResultsResultset.size).isEqualTo(1)
      expectThat(taskResultsResultset.get(0)).isEqualTo("myid0")

      val taskStatesResultset = database.context.select()
        .from(taskStatesTable)
        .fetch("task_id", String::class.java)
        .toTypedArray()

      expectThat(taskStatesResultset.size).isEqualTo(1)
      expectThat(taskStatesResultset.get(0)).isEqualTo("myid0")

      val taskOutputsResultset = database.context.select()
        .from(taskOutputsTable)
        .fetch("task_id", String::class.java)
        .toTypedArray()

      expectThat(taskOutputsResultset.size).isEqualTo(1)
      expectThat(taskOutputsResultset.get(0)).isEqualTo("myid0")
    }
  }

  private inner class Fixture {
    val database = SqlTestUtil.initTcMysqlDatabase()!!

    val subject = SqlTaskCleanupAgent(
      jooq = database.context,
      clock = Clock.systemDefaultZone(),
      registry = NoopRegistry(),
      SqlTaskCleanupAgentProperties()
    )

    val objectMapper = ObjectMapper()
  }
}
