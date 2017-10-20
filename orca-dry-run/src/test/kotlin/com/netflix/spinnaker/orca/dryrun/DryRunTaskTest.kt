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

import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.q.pipeline
import com.netflix.spinnaker.orca.q.singleTaskStage
import com.netflix.spinnaker.orca.q.stage
import com.netflix.spinnaker.spek.shouldEqual
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DryRunTaskTest : Spek({

  val subject = DryRunTask()

  describe("running the task") {
    given("a stage that will evaluate successfully") {
      val realPipeline = pipeline {
        stage {
          refId = "1"
          type = singleTaskStage.type
          context["something"] = "covfefe"
          context["fromConfig"] = "covfefe"
          outputs["result"] = "covfefe"
          status = SUCCEEDED
        }
      }
      val realStage = realPipeline.stageByRef("1")

      val testPipeline = pipeline {
        stage {
          refId = "1"
          type = singleTaskStage.type
          context["fromConfig"] = "covfefe"
        }
        trigger["lastSuccessfulExecution"] = realPipeline
      }
      val stage = testPipeline.stageByRef("1")

      on("running the stage") {
        val result = subject.execute(stage)

        it("returns the same status as the real stage") {
          result.status shouldEqual realStage.status
        }

        it("duplicates the outputs of the real stage") {
          result.outputs shouldEqual realStage.outputs
        }

        it("replicates the stage context after execution") {
          result.context shouldEqual realStage.context
        }
      }
    }

    given("a mismatch in context values") {
      val realPipeline = pipeline {
        stage {
          refId = "1"
          type = singleTaskStage.type
          context["something"] = "covfefe"
          status = SUCCEEDED
        }
      }

      val testPipeline = pipeline {
        stage {
          refId = "1"
          type = singleTaskStage.type
          context["something"] = "dotard"
        }
        trigger["lastSuccessfulExecution"] = realPipeline
      }
      val stage = testPipeline.stageByRef("1")

      on("running the stage") {
        val result = subject.execute(stage)

        it("fails") {
          result.status shouldEqual TERMINAL
        }

        it("puts the new value in the context") {
          result.context["something"] shouldEqual stage.context["something"]
        }

        it("adds an error to the stage outputs") {
          result.outputs["dryRunResult"] shouldEqual mapOf(
            "context" to mapOf(
              "something" to "Expected \"covfefe\" but found \"dotard\"."
            )
          )
        }
      }
    }

    given("a mismatch in context values in a blacklisted key") {
      val realPipeline = pipeline {
        stage {
          refId = "1"
          type = singleTaskStage.type
          context["amiSuffix"] = "1234"
          status = SUCCEEDED
        }
      }

      val testPipeline = pipeline {
        stage {
          refId = "1"
          type = singleTaskStage.type
          context["amiSuffix"] = "5678"
        }
        trigger["lastSuccessfulExecution"] = realPipeline
      }
      val stage = testPipeline.stageByRef("1")

      on("running the stage") {
        val result = subject.execute(stage)

        it("succeeds") {
          result.status shouldEqual SUCCEEDED
        }
      }
    }

    given("a mismatch in context values in a key matching a blacklisted pattern") {
      val realPipeline = pipeline {
        stage {
          refId = "1"
          type = singleTaskStage.type
          context["kato.whatever"] = "1234"
          status = SUCCEEDED
        }
      }

      val testPipeline = pipeline {
        stage {
          refId = "1"
          type = singleTaskStage.type
          context["kato.whatever"] = "5678"
        }
        trigger["lastSuccessfulExecution"] = realPipeline
      }
      val stage = testPipeline.stageByRef("1")

      on("running the stage") {
        val result = subject.execute(stage)

        it("succeeds") {
          result.status shouldEqual SUCCEEDED
        }
      }
    }

    given("a stage that was skipped previously") {
      val realPipeline = pipeline {
        stage {
          refId = "1"
          type = singleTaskStage.type
          status = SKIPPED
        }
      }

      val testPipeline = pipeline {
        stage {
          refId = "1"
          type = singleTaskStage.type
        }
        trigger["lastSuccessfulExecution"] = realPipeline
      }
      val stage = testPipeline.stageByRef("1")

      on("running the stage") {
        val result = subject.execute(stage)

        it("fails") {
          result.status shouldEqual TERMINAL
        }

        it("adds an error to the stage outputs") {
          result.outputs["dryRunResult"] shouldEqual mapOf(
            "errors" to listOf("Expected stage to be skipped.")
          )
        }
      }
    }
  }
})
