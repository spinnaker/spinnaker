/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.dryrun

import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.dryrun.stub.OutputStub
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DryRunTaskTest : Spek({

  val outputStub: OutputStub = mock()
  val subject = DryRunTask(listOf(outputStub))

  describe("running the task") {

    val pipeline = pipeline {
      stage {
        type = "deploy"
        refId = "1"
      }
      stage {
        type = "bake"
        refId = "2"
      }
      trigger = DefaultTrigger("manual", null, "fzlem@netflix.com")
    }

    given("a stage with no outputs in the trigger and no output stub") {

      beforeGroup {
        whenever(outputStub.supports(any())) doReturn false
      }

      afterGroup {
        reset(outputStub)
      }

      var result: TaskResult? = null

      on("running the task") {
        result = subject.execute(pipeline.stageByRef("1"))
      }

      it("should return success") {
        assertThat(result!!.status).isEqualTo(SUCCEEDED)
      }

      it("should create no outputs") {
        assertThat(result!!.outputs).isEmpty()
      }

      it("should not try to stub output") {
        verify(outputStub, never()).outputs(any())
      }
    }

    given("a stage with an output stub") {

      val stubOutput = mapOf("negative" to "covfefe")
      beforeGroup {
        whenever(outputStub.supports(any())) doAnswer { it.getArgument<Stage>(0).type == "bake" }
        whenever(outputStub.outputs(pipeline.stageByRef("2"))) doReturn stubOutput
      }

      afterGroup {
        reset(outputStub)
      }

      var result: TaskResult? = null

      on("running the task") {
        result = subject.execute(pipeline.stageByRef("2"))
      }

      it("should return success") {
        assertThat(result!!.status).isEqualTo(SUCCEEDED)
      }

      it("should have stubbed outputs") {
        assertThat(result!!.outputs).isEqualTo(stubOutput)
      }
    }
  }
})
