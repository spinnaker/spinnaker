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

import com.netflix.spinnaker.orca.pipeline.CheckPreconditionsStage
import com.netflix.spinnaker.orca.pipeline.DefaultStageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage

class DryRunStageDefinitionBuilderFactory(
  stageDefinitionBuilders: Collection<StageDefinitionBuilder>
) : DefaultStageDefinitionBuilderFactory(stageDefinitionBuilders) {

  override fun builderFor(stage: Stage): StageDefinitionBuilder =
    stage.execution.let { execution ->
      super.builderFor(stage).let {
        if (stage.isExpressionPreconditionStage()) {
          it
        } else if (execution.trigger.type == "dryrun") {
          DryRunStage(it)
        } else {
          it
        }
      }
    }

  private fun Stage.isExpressionPreconditionStage() =
    isPreconditionStage() && (isExpressionChild() || isExpressionParent())

  private fun Stage.isPreconditionStage() =
    type == CheckPreconditionsStage.PIPELINE_CONFIG_TYPE

  private fun Stage.isExpressionChild() =
    context["preconditionType"] == "expression"

  @Suppress("UNCHECKED_CAST")
  private fun Stage.isExpressionParent() =
    (context["preconditions"] as Iterable<Map<String, Any>>?)?.run {
      all { it["type"] == "expression" }
    } == true
}
