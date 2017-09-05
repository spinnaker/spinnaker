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

import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage
import com.netflix.spinnaker.orca.pipeline.TaskNode.Builder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import java.lang.RuntimeException

val singleTaskStage = object : StageDefinitionBuilder {
  override fun getType() = "singleTaskStage"
  override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
    builder.withTask("dummy", DummyTask::class.java)
  }
}

val zeroTaskStage = object : StageDefinitionBuilder {
  override fun getType() = "zeroTaskStage"
}

val multiTaskStage = object : StageDefinitionBuilder {
  override fun getType() = "multiTaskStage"
  override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
    builder
      .withTask("dummy1", DummyTask::class.java)
      .withTask("dummy2", DummyTask::class.java)
      .withTask("dummy3", DummyTask::class.java)
  }
}

val stageWithSyntheticBefore = object : StageDefinitionBuilder {
  override fun getType() = "stageWithSyntheticBefore"
  override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
    builder.withTask("dummy", DummyTask::class.java)
  }

  override fun <T : Execution<T>> aroundStages(stage: Stage<T>) = listOf(
    newStage(stage.execution, singleTaskStage.type, "pre1", mutableMapOf(), stage, STAGE_BEFORE),
    newStage(stage.execution, singleTaskStage.type, "pre2", mutableMapOf(), stage, STAGE_BEFORE)
  )
}

val stageWithSyntheticBeforeAndNoTasks = object : StageDefinitionBuilder {
  override fun getType() = "stageWithSyntheticBeforeAndNoTasks"

  override fun <T : Execution<T>> aroundStages(stage: Stage<T>) = listOf(
    newStage(stage.execution, singleTaskStage.type, "pre", mutableMapOf(), stage, STAGE_BEFORE)
  )
}

val stageWithSyntheticBeforeAndAfterAndNoTasks = object : StageDefinitionBuilder {
  override fun getType() = "stageWithSyntheticBeforeAndAfterAndNoTasks"

  override fun <T : Execution<T>> aroundStages(stage: Stage<T>) = listOf(
    newStage(stage.execution, singleTaskStage.type, "pre", mutableMapOf(), stage, STAGE_BEFORE),
    newStage(stage.execution, singleTaskStage.type, "post", mutableMapOf(), stage, STAGE_AFTER)
  )
}

val stageWithSyntheticAfter = object : StageDefinitionBuilder {
  override fun getType() = "stageWithSyntheticAfter"
  override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
    builder.withTask("dummy", DummyTask::class.java)
  }

  override fun <T : Execution<T>> aroundStages(stage: Stage<T>) = listOf(
    newStage(stage.execution, singleTaskStage.type, "post1", mutableMapOf(), stage, STAGE_AFTER),
    newStage(stage.execution, singleTaskStage.type, "post2", mutableMapOf(), stage, STAGE_AFTER)
  )
}

val stageWithSyntheticAfterAndNoTasks = object : StageDefinitionBuilder {
  override fun getType() = "stageWithSyntheticAfterAndNoTasks"

  override fun <T : Execution<T>> aroundStages(stage: Stage<T>) = listOf(
    newStage(stage.execution, singleTaskStage.type, "post", mutableMapOf(), stage, STAGE_AFTER)
  )
}

val stageWithNestedSynthetics = object : StageDefinitionBuilder {
  override fun getType() = "stageWithNestedSynthetics"

  override fun <T : Execution<T>> aroundStages(stage: Stage<T>) = listOf(
    newStage(stage.execution, stageWithSyntheticBefore.type, "post", mutableMapOf(), stage, STAGE_AFTER)
  )
}

val stageWithParallelBranches = object : StageDefinitionBuilder {
  override fun <T : Execution<T>> parallelStages(stage: Stage<T>) =
    listOf(
      newStage(stage.execution, "singleTaskStage", "run in us-east-1", mapOf("region" to "us-east-1"), stage, STAGE_BEFORE),
      newStage(stage.execution, "singleTaskStage", "run in us-west-2", mapOf("region" to "us-west-2"), stage, STAGE_BEFORE),
      newStage(stage.execution, "singleTaskStage", "run in eu-west-1", mapOf("region" to "eu-west-1"), stage, STAGE_BEFORE)
    )

  override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
    builder.withTask("post-branch", DummyTask::class.java)
  }
}

val rollingPushStage = object : StageDefinitionBuilder {
  override fun getType() = "rolling"
  override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
    builder
      .withTask("beforeLoop", DummyTask::class.java)
      .withLoop { subGraph ->
        subGraph
          .withTask("startLoop", DummyTask::class.java)
          .withTask("inLoop", DummyTask::class.java)
          .withTask("endLoop", DummyTask::class.java)
      }
      .withTask("afterLoop", DummyTask::class.java)
  }
}

val webhookStage = object : StageDefinitionBuilder {
  override fun getType() = "webhook"
  override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
    builder.withTask("createWebhook", DummyTask::class.java)
  }
}

val failPlanningStage = object : StageDefinitionBuilder {
  override fun getType() = "failPlanning"
  override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
    throw RuntimeException("o noes")
  }
}
