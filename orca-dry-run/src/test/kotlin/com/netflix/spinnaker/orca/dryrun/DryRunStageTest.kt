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

import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.q.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DryRunStageTest : Spek({

  fun StageDefinitionBuilder.plan(stage: Stage) {
    stage.type = type
    buildTasks(stage)
    buildSyntheticStages(stage)
  }

  setOf(zeroTaskStage, singleTaskStage, multiTaskStage, stageWithSyntheticBefore, stageWithSyntheticBeforeAndNoTasks).forEach { proxiedStage ->
    describe("building tasks for a ${proxiedStage.type}") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = proxiedStage.type
        }
      }

      val subject = DryRunStage(proxiedStage)

      on("planning the stage") {
        subject.plan(pipeline.stageByRef("1"))
      }

      it("constructs a single task") {
        pipeline.stageByRef("1").tasks.let {
          assertThat(it.size).isEqualTo(1)
          assertThat(it.first().implementingClass).isEqualTo(DryRunTask::class.qualifiedName)
        }
      }
    }
  }

  setOf(zeroTaskStage, singleTaskStage, multiTaskStage).forEach { proxiedStage ->
    describe("building synthetic stages for a ${proxiedStage.type}") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = proxiedStage.type
        }
      }

      val subject = DryRunStage(proxiedStage)

      on("planning the stage") {
        subject.plan(pipeline.stageByRef("1"))
      }

      it("does not build any synthetic stages") {
        assertThat(pipeline.stages.size).isEqualTo(1)
      }
    }
  }

  setOf(stageWithSyntheticBefore, stageWithSyntheticBeforeAndNoTasks).forEach { proxiedStage ->
    describe("building synthetic stages for a ${proxiedStage.type}") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = proxiedStage.type
        }
      }

      val subject = DryRunStage(proxiedStage)

      on("planning the stage") {
        subject.plan(pipeline.stageByRef("1"))
      }

      it("builds the usual synthetic stages") {
        assertThat(pipeline.stages.size).isGreaterThan(1)
      }
    }
  }

  setOf(stageWithParallelBranches).forEach { proxiedStage ->
    describe("building parallel stages for a ${proxiedStage.type}") {
      val pipeline = pipeline {
        stage {
          refId = "1"
          type = proxiedStage.type
        }
      }

      val subject = DryRunStage(proxiedStage)

      on("planning the stage") {
        subject.plan(pipeline.stageByRef("1"))
      }

      it("builds the usual parallel stages") {
        assertThat(pipeline.stages.size).isGreaterThan(1)
      }
    }
  }
})
