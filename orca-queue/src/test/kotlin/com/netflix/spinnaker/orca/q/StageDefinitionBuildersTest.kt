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

package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object StageDefinitionBuildersTest : Spek({
  val stageBuilder = MyStageDefinitionBuilder()

  describe("refIds for synthetic stages") {
    val pipeline = pipeline {
      stage {
        id = "1"
        refId = "1"
      }
    }

    on("adding a synthetic stage") {
      stageBuilder.buildAfterStages(
        pipeline.stageById("1"),
        listOf(makeStage("2", "1", pipeline))
      )
    }

    it("sets the refId with _no_ offset") {
      val stage = pipeline.stageById("2")
      assertThat(stage.refId).isEqualTo("1>1")
      assertThat(stage.requisiteStageRefIds).isEmpty()
    }

    on("adding another synthetic stage") {
      stageBuilder.buildAfterStages(
        pipeline.stageById("1"),
        listOf(makeStage("3", "1", pipeline))
      )
    }

    it("sets the refId with an initial offset") {
      val stage = pipeline.stageById("3")
      assertThat(stage.refId).isEqualTo("1>2")
      assertThat(stage.requisiteStageRefIds).containsExactly("1>1")
    }
  }
})

private fun makeStage(stageId: String, syntheticParentStageId: String, pipeline: Execution) = stage {
  id = stageId
  parentStageId = pipeline.stageById(syntheticParentStageId).id
  syntheticStageOwner = SyntheticStageOwner.STAGE_AFTER
}

private class MyStageDefinitionBuilder : StageDefinitionBuilder
