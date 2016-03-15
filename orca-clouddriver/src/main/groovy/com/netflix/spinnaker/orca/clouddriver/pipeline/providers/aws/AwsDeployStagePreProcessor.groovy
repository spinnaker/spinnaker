/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AwsDeployStagePreProcessor implements DeployStagePreProcessor {
  @Autowired
  ApplySourceServerGroupCapacityStage applySourceServerGroupSnapshotStage

  @Override
  List<DeployStagePreProcessor.StepDefinition> additionalSteps() {
    return [
        new DeployStagePreProcessor.StepDefinition(
          name: "snapshotSourceServerGroup",
          taskClass: CaptureSourceServerGroupCapacityTask
        )
    ]
  }

  @Override
  List<DeployStagePreProcessor.StageDefinition> afterStageDefinitions() {
    return [
        new DeployStagePreProcessor.StageDefinition(
          name: "restoreMinCapacityFromSnapshot",
          stageDefinitionBuilder: applySourceServerGroupSnapshotStage,
          context: [:]
        )
    ]
  }

  @Override
  boolean supports(Stage stage) {
    def stageData = stage.mapTo(StageData)
    return stageData.useSourceCapacity && stageData.cloudProvider == "aws"
  }
}
