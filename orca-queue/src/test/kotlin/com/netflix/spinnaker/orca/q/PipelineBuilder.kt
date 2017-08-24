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

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import java.lang.System.currentTimeMillis
import kotlin.reflect.jvm.jvmName

fun pipeline(init: Pipeline.() -> Unit = {}): Pipeline {
  val pipeline = Pipeline("covfefe")
  pipeline.buildTime = currentTimeMillis()
  pipeline.init()
  return pipeline
}

fun <T : Execution<T>> T.stage(init: Stage<T>.() -> Unit): Stage<T> {
  val stage = Stage<T>()
  stage.execution = this
  stage.type = "test"
  stage.refId = "1"
  stages.add(stage)
  stage.init()
  return stage
}

fun <T : Execution<T>> Stage<T>.task(init: Task.() -> Unit): Task {
  val task = Task()
  task.implementingClass = DummyTask::class.jvmName
  task.name = "dummy"
  tasks.add(task)
  task.init()
  return task
}
