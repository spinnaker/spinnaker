/*
 * Copyright 2020 Netflix, Inc.
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
 */

package com.netflix.spinnaker.orca.api.test

import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl
import com.netflix.spinnaker.orca.pipeline.tasks.NoOpTask
import java.lang.System.currentTimeMillis

/**
 * Build a pipeline.
 */
fun pipeline(init: PipelineExecution.() -> Unit = {}): PipelineExecution {
  val pipeline = PipelineExecutionImpl(PIPELINE, "covfefe")
  pipeline.trigger = DefaultTrigger("manual")
  pipeline.startTime = currentTimeMillis()
  pipeline.buildTime = currentTimeMillis()
  pipeline.init()
  return pipeline
}

/**
 * Build a stage outside the context of an execution.
 */
fun stage(init: StageExecution.() -> Unit): StageExecution {
  val stage = StageExecutionImpl()
  stage.execution = pipeline()
  stage.type = "test"
  stage.refId = "1"
  stage.execution.stages.add(stage)
  stage.init()
  return stage
}

/**
 * Build a top-level stage. Use in the context of [#pipeline].
 *
 * Automatically hooks up execution.
 */
fun PipelineExecution.stage(init: StageExecution.() -> Unit): StageExecution {
  val stage = StageExecutionImpl()
  stage.execution = this
  stage.type = "test"
  stage.refId = "1"
  stages.add(stage)
  stage.init()
  return stage
}

/**
 * Build a synthetic stage. Use in the context of [#stage].
 *
 * Automatically hooks up execution and parent stage.
 */
fun StageExecution.stage(init: StageExecution.() -> Unit): StageExecution {
  val stage = StageExecutionImpl()
  stage.execution = execution
  stage.type = "test"
  stage.refId = "$refId<1"
  stage.parentStageId = id
  stage.syntheticStageOwner = STAGE_BEFORE
  execution.stages.add(stage)
  stage.init()
  return stage
}

/**
 * Build a task. Use in the context of [#stage].
 */
fun StageExecution.task(init: TaskExecution.() -> Unit): TaskExecution {
  val task = TaskExecutionImpl()
  task.implementingClass = NoOpTask::class.java.name
  task.name = "dummy"
  tasks.add(task)
  task.init()
  return task
}
