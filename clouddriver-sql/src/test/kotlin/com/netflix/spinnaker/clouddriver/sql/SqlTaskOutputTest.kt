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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Clock

class SqlTaskOutputTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    after {
      SqlTestUtil.cleanupDb(database.context)
    }

    context("task output") {
      test("verify if the task outputs with null/empty values can be stored and retrieved successfully from the db") {
        val t1 = subject.create("TEST", "Test Status")

        t1.updateOutput("some-manifest", "TEST", null, "")
        assert(t1.outputs[0].manifest == "some-manifest")
        assert(t1.outputs[0].phase == "TEST")
        assert(t1.outputs[0].stdOut.isNullOrBlank())
        assert(t1.outputs[0].stdError.isNullOrBlank())
      }

      test("verify if the task outputs can be stored and retrieved successfully from the db") {
        val t1 = subject.create("TEST", "Test Status")

        t1.updateOutput("some-manifest", "TEST", "output", "")
        assert(t1.outputs[0].manifest == "some-manifest")
        assert(t1.outputs[0].phase == "TEST")
        assert(t1.outputs[0].stdOut == "output")
        assert(t1.outputs[0].stdError.isNullOrBlank())
      }

      test("task has outputs from multiple manifests") {
        val t1 = subject.create("TEST", "Test Status")

        t1.updateOutput("some-manifest", "TEST", "output", "")
        t1.updateOutput("some-manifest-2", "Deploy", "other output", "")
        assert(t1.outputs.size == 2)
        assert(t1.outputs[0].manifest == "some-manifest")
        assert(t1.outputs[0].phase == "TEST")
        assert(t1.outputs[0].stdOut == "output")
        assert(t1.outputs[0].stdError == "")
        assert(t1.outputs[1].manifest == "some-manifest-2")
        assert(t1.outputs[1].phase == "Deploy")
        assert(t1.outputs[1].stdOut == "other output")
        assert(t1.outputs[1].stdError.isNullOrBlank())
      }

      test("multiple tasks with only one task having outputs from multiple manifests") {
        val t1 = subject.create("TEST", "Test Status")
        val t2 = subject.create("TEST", "Test Status")

        t1.updateOutput("some-manifest", "TEST", "output", "")
        t1.updateOutput("some-manifest-2", "Deploy", "other output", "")
        assert(t1.outputs.size == 2)
        assert(t1.outputs[0].manifest == "some-manifest")
        assert(t1.outputs[0].phase == "TEST")
        assert(t1.outputs[0].stdOut == "output")
        assert(t1.outputs[0].stdError.isNullOrBlank())
        assert(t1.outputs[1].manifest == "some-manifest-2")
        assert(t1.outputs[1].phase == "Deploy")
        assert(t1.outputs[1].stdOut == "other output")
        assert(t1.outputs[1].stdError.isNullOrBlank())
        assert(t2.outputs.isEmpty())
      }
    }
  }

  private inner class Fixture {
    val database = SqlTestUtil.initTcMysqlDatabase()!!

    val subject = SqlTaskRepository(
      jooq = database.context,
      mapper = ObjectMapper().apply {
        registerModules(KotlinModule(), JavaTimeModule())
      },
      clock = Clock.systemDefaultZone(),
      poolName = ConnectionPools.TASKS.value
    )
  }
}
