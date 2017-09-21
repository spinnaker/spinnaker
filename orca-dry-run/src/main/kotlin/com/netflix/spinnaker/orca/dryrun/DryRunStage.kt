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

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode.Builder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import kotlin.reflect.KClass

class DryRunStage(private val delegate: StageDefinitionBuilder) : StageDefinitionBuilder {

  override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
    builder.withTask("dry run", DryRunTask::class)
  }

  override fun <T : Execution<T>> aroundStages(stage: Stage<T>): List<Stage<T>>
    = delegate.aroundStages(stage)

  override fun <T : Execution<T>> parallelStages(stage: Stage<T>): List<Stage<T>>
    = delegate.parallelStages(stage)

  override fun getType() = delegate.type

  private fun Builder.withTask(name: String, type: KClass<out Task>) =
    withTask(name, type.java)
}
