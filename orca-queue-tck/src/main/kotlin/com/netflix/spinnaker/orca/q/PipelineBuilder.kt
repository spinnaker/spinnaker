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

import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import com.netflix.spinnaker.orca.pipeline.model.Task
import java.lang.System.currentTimeMillis

/**
 * Build a pipeline.
 */
fun pipeline(init: Execution.() -> Unit = {}): Execution {
  val pipeline = Execution(PIPELINE, "covfefe")
  pipeline.trigger = DefaultTrigger("manual")
  pipeline.buildTime = currentTimeMillis()
  pipeline.init()
  return pipeline
}

/**
 * Build a top-level stage. Use in the context of [#pipeline].
 *
 * Automatically hooks up execution.
 */
fun Execution.stage(init: Stage.() -> Unit): Stage {
  val stage = Stage()
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
fun Stage.stage(init: Stage.() -> Unit): Stage {
  val stage = Stage()
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
fun Stage.task(init: Task.() -> Unit): Task {
  val task = Task()
  task.implementingClass = DummyTask::class.java.name
  task.name = "dummy"
  tasks.add(task)
  task.init()
  return task
}
