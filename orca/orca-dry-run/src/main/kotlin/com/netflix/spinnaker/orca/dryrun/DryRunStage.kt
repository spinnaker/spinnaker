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

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode.Builder
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.ext.withTask

class DryRunStage(private val delegate: StageDefinitionBuilder) : StageDefinitionBuilder {

  override fun taskGraph(stage: StageExecution, builder: Builder) {
    builder.withTask<DryRunTask>("dry run")
  }

  override fun beforeStages(parent: StageExecution, graph: StageGraphBuilder) =
    delegate.beforeStages(parent, graph)

  override fun afterStages(parent: StageExecution, graph: StageGraphBuilder) =
    delegate.afterStages(parent, graph)

  override fun onFailureStages(stage: StageExecution, graph: StageGraphBuilder) =
    delegate.onFailureStages(stage, graph)

  override fun getType() = delegate.type
}
