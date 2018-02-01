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
import com.netflix.spinnaker.orca.pipeline.model.Stage

class DryRunStage(private val delegate: StageDefinitionBuilder) : StageDefinitionBuilder {

  override fun taskGraph(stage: Stage, builder: Builder) {
    builder.withTask<DryRunTask>("dry run")
  }

  override fun aroundStages(stage: Stage): List<Stage>
    = delegate.aroundStages(stage)

  override fun parallelStages(stage: Stage): List<Stage>
    = delegate.parallelStages(stage)

  override fun getType() = delegate.type

  private inline fun <reified T : Task> Builder.withTask(name: String) =
    withTask(name, T::class.java)
}
