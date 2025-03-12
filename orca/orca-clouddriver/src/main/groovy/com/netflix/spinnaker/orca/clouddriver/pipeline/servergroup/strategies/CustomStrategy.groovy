/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CloneServerGroupStage
import com.netflix.spinnaker.orca.front50.pipeline.PipelineStage
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner
import com.netflix.spinnaker.orca.pipeline.StageExecutionFactory
import org.springframework.stereotype.Component

@Component
class CustomStrategy implements Strategy {

  final String name = "custom"

  @Override
  List<StageExecution> composeBeforeStages(StageExecution parent) {
    return Collections.emptyList()
  }

  @Override
  List<StageExecution> composeAfterStages(StageExecution stage) {
    def cleanupConfig = AbstractDeployStrategyStage.CleanupConfig.fromStage(stage)

    Map parameters = [
      application                            : stage.context.application,
      credentials                            : cleanupConfig.account,
      cluster                                : cleanupConfig.cluster,
      moniker                                : cleanupConfig.moniker,
      (cleanupConfig.location.singularType()): cleanupConfig.location.value,
      cloudProvider                          : cleanupConfig.cloudProvider,
      strategy                               : true,
      clone                                  : stage.type == CloneServerGroupStage.PIPELINE_CONFIG_TYPE,
      parentPipelineId                       : stage.execution.id,
      parentStageId                          : stage.id
    ]

    if (stage.context.pipelineParameters) {
      parameters.putAll(stage.context.pipelineParameters as Map)
    }

    Map modifyCtx = [
      application        : stage.context.application,
      pipelineApplication: stage.context.strategyApplication,
      pipelineId         : stage.context.strategyPipeline,
      pipelineParameters : parameters
    ]

    return [
        StageExecutionFactory.newStage(
        stage.execution,
        PipelineStage.PIPELINE_CONFIG_TYPE,
        "pipeline",
        modifyCtx,
        stage,
        SyntheticStageOwner.STAGE_AFTER
      )
    ]
  }

  @Override
  boolean replacesBasicSteps() {
    return true
  }
}
