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
import com.netflix.spinnaker.orca.pipeline.model.DryRunTrigger
import com.netflix.spinnaker.orca.q.pipeline
import com.netflix.spinnaker.orca.q.stage
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
        type = "deploy"
        refId = "2"
      }
      stage {
        type = "bake"
        refId = "3"
      }
      stage {
        type = "bake"
        refId = "4"
      }
      trigger = DryRunTrigger(
        mapOf("2" to mapOf("foo" to "bar"), "4" to mapOf("foo" to "bar"))
      )
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

    given("a stage with outputs overridden in the trigger") {

      beforeGroup {
        whenever(outputStub.supports(any())) doReturn false
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

      it("should copy outputs from the trigger") {
        assertThat(result!!.outputs).isEqualTo(mapOf("foo" to "bar"))
      }

      it("should not try to stub output") {
        verify(outputStub, never()).outputs(any())
      }
    }

    given("a stage with an output stub") {

      val stubOutput = mapOf("negative" to "covfefe")
      beforeGroup {
        whenever(outputStub.supports("bake")) doReturn true
        whenever(outputStub.outputs(pipeline.stageByRef("3"))) doReturn stubOutput
      }

      afterGroup {
        reset(outputStub)
      }

      var result: TaskResult? = null

      on("running the task") {
        result = subject.execute(pipeline.stageByRef("3"))
      }

      it("should return success") {
        assertThat(result!!.status).isEqualTo(SUCCEEDED)
      }

      it("should have stubbed outputs") {
        assertThat(result!!.outputs).isEqualTo(stubOutput)
      }
    }

    given("a stage with an output stub and trigger data") {

      val stubOutput = mapOf("negative" to "covfefe", "foo" to "baz")
      beforeGroup {
        whenever(outputStub.supports("bake")) doReturn true
        whenever(outputStub.outputs(pipeline.stageByRef("4"))) doReturn stubOutput
      }

      afterGroup {
        reset(outputStub)
      }

      var result: TaskResult? = null

      on("running the task") {
        result = subject.execute(pipeline.stageByRef("4"))
      }

      it("should return success") {
        assertThat(result!!.status).isEqualTo(SUCCEEDED)
      }

      it("trigger outputs take precedence") {
        assertThat(result!!.outputs["negative"]).isEqualTo("covfefe")
        assertThat(result!!.outputs["foo"]).isEqualTo("bar")
      }
    }
  }
})
