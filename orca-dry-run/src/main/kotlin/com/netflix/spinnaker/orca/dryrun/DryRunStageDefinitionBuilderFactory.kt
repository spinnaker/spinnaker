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

import com.netflix.spinnaker.orca.StageResolver
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.CheckPreconditionsStage
import com.netflix.spinnaker.orca.pipeline.DefaultStageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder

class DryRunStageDefinitionBuilderFactory(
  stageResolver: StageResolver
) : DefaultStageDefinitionBuilderFactory(stageResolver) {

  override fun builderFor(stage: StageExecution): StageDefinitionBuilder =
    stage.execution.let { execution ->
      super.builderFor(stage).let {
        if (!execution.trigger.isDryRun || stage.shouldExecuteNormallyInDryRun) {
          it
        } else {
          DryRunStage(it)
        }
      }
    }

  private val StageExecution.shouldExecuteNormallyInDryRun: Boolean
    get() = isManualJudgment ||
      isPipeline ||
      isExpressionPrecondition ||
      isFindImage ||
      isDetermineTargetServerGroup ||
      isRollbackCluster ||
      isEvalVariables

  private val StageExecution.isManualJudgment: Boolean
    get() = type == "manualJudgment"

  private val StageExecution.isPipeline: Boolean
    get() = type == "pipeline"

  private val StageExecution.isFindImage: Boolean
    get() = type in setOf("findImage", "findImageFromTags")

  private val StageExecution.isDetermineTargetServerGroup: Boolean
    get() = type == "determineTargetServerGroup"

  private val StageExecution.isExpressionPrecondition: Boolean
    get() = isPreconditionStage && (isExpressionChild || isExpressionParent)

  private val StageExecution.isPreconditionStage: Boolean
    get() = type == CheckPreconditionsStage.PIPELINE_CONFIG_TYPE

  private val StageExecution.isExpressionChild: Boolean
    get() = context["preconditionType"] == "expression"

  @Suppress("UNCHECKED_CAST")
  private val StageExecution.isExpressionParent: Boolean
    get() = (context["preconditions"] as Iterable<Map<String, Any>>?)?.run {
      all { it["type"] == "expression" }
    } == true

  private val StageExecution.isRollbackCluster: Boolean
    get() = type == "rollbackCluster"

  private val StageExecution.isEvalVariables: Boolean
    get() = type == "evaluateVariables"
}
