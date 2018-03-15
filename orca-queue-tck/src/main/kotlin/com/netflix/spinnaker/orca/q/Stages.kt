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

package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.ext.withTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage
import com.netflix.spinnaker.orca.pipeline.TaskNode.Builder
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import java.lang.RuntimeException

val singleTaskStage = object : StageDefinitionBuilder {
  override fun getType() = "singleTaskStage"
  override fun taskGraph(stage: Stage, builder: Builder) {
    builder.withTask<DummyTask>("dummy")
  }
}

val zeroTaskStage = object : StageDefinitionBuilder {
  override fun getType() = "zeroTaskStage"
}

val multiTaskStage = object : StageDefinitionBuilder {
  override fun getType() = "multiTaskStage"
  override fun taskGraph(stage: Stage, builder: Builder) {
    builder
      .withTask<DummyTask>("dummy1")
      .withTask<DummyTask>("dummy2")
      .withTask<DummyTask>("dummy3")
  }
}

val stageWithSyntheticBefore = object : StageDefinitionBuilder {
  override fun getType() = "stageWithSyntheticBefore"
  override fun taskGraph(stage: Stage, builder: Builder) {
    builder.withTask<DummyTask>("dummy")
  }

  override fun aroundStages(stage: Stage) = listOf(
    newStage(stage.execution, singleTaskStage.type, "pre1", stage.context, stage, STAGE_BEFORE),
    newStage(stage.execution, singleTaskStage.type, "pre2", stage.context, stage, STAGE_BEFORE)
  )
}

val stageWithSyntheticOnFailure = object : StageDefinitionBuilder {
  override fun getType() = "stageWithSyntheticOnFailure"
  override fun taskGraph(stage: Stage, builder: Builder) {
    builder.withTask<DummyTask>("dummy")
  }

  override fun onFailureStages(stage: Stage, graph: StageGraphBuilder) {
    val stage1 = graph.add {
      it.type = singleTaskStage.type
      it.name = "onFailure1"
      it.context = stage.context
    }
    graph.connect(stage1) {
      it.type = singleTaskStage.type
      it.name = "onFailure2"
      it.context = stage.context
    }
  }
}

val stageWithSyntheticBeforeAndNoTasks = object : StageDefinitionBuilder {
  override fun getType() = "stageWithSyntheticBeforeAndNoTasks"

  override fun aroundStages(stage: Stage) = listOf(
    newStage(stage.execution, singleTaskStage.type, "pre", stage.context, stage, STAGE_BEFORE)
  )
}

val stageWithSyntheticBeforeAndAfterAndNoTasks = object : StageDefinitionBuilder {
  override fun getType() = "stageWithSyntheticBeforeAndAfterAndNoTasks"

  override fun aroundStages(stage: Stage) = listOf(
    newStage(stage.execution, singleTaskStage.type, "pre", stage.context, stage, STAGE_BEFORE),
    newStage(stage.execution, singleTaskStage.type, "post", stage.context, stage, STAGE_AFTER)
  )
}

val stageWithSyntheticAfter = object : StageDefinitionBuilder {
  override fun getType() = "stageWithSyntheticAfter"
  override fun taskGraph(stage: Stage, builder: Builder) {
    builder.withTask<DummyTask>("dummy")
  }

  override fun aroundStages(stage: Stage) = listOf(
    newStage(stage.execution, singleTaskStage.type, "post1", stage.context, stage, STAGE_AFTER),
    newStage(stage.execution, singleTaskStage.type, "post2", stage.context, stage, STAGE_AFTER)
  )
}

val stageWithSyntheticAfterAndNoTasks = object : StageDefinitionBuilder {
  override fun getType() = "stageWithSyntheticAfterAndNoTasks"

  override fun aroundStages(stage: Stage) = listOf(
    newStage(stage.execution, singleTaskStage.type, "post", stage.context, stage, STAGE_AFTER)
  )
}

val stageWithNestedSynthetics = object : StageDefinitionBuilder {
  override fun getType() = "stageWithNestedSynthetics"

  override fun aroundStages(stage: Stage) = listOf(
    newStage(stage.execution, stageWithSyntheticBefore.type, "post", stage.context, stage, STAGE_AFTER)
  )
}

val stageWithParallelBranches = object : StageDefinitionBuilder {
  override fun parallelStages(stage: Stage) =
    listOf("us-east-1", "us-west-2", "eu-west-1")
      .map { region ->
        newStage(stage.execution, singleTaskStage.type, "run in $region", stage.context + mapOf("region" to region), stage, STAGE_BEFORE)
      }

  override fun taskGraph(stage: Stage, builder: Builder) {
    builder.withTask<DummyTask>("post-branch")
  }
}

val rollingPushStage = object : StageDefinitionBuilder {
  override fun getType() = "rolling"
  override fun taskGraph(stage: Stage, builder: Builder) {
    builder
      .withTask<DummyTask>("beforeLoop")
      .withLoop { subGraph ->
        subGraph
          .withTask<DummyTask>("startLoop")
          .withTask<DummyTask>("inLoop")
          .withTask<DummyTask>("endLoop")
      }
      .withTask<DummyTask>("afterLoop")
  }
}

val webhookStage = object : StageDefinitionBuilder {
  override fun getType() = "webhook"
  override fun taskGraph(stage: Stage, builder: Builder) {
    builder.withTask<DummyTask>("createWebhook")
  }
}

val failPlanningStage = object : StageDefinitionBuilder {
  override fun getType() = "failPlanning"
  override fun taskGraph(stage: Stage, builder: Builder) {
    throw RuntimeException("o noes")
  }
}
