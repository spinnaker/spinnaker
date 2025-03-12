/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.gce;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityTask;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.DeployStagePreProcessor;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GceDeployStagePreProcessor implements DeployStagePreProcessor {
  private final ApplySourceServerGroupCapacityStage applySourceServerGroupSnapshotStage;
  private final TargetServerGroupResolver targetServerGroupResolver;

  @Autowired
  public GceDeployStagePreProcessor(
      ApplySourceServerGroupCapacityStage applySourceServerGroupCapacityStage,
      TargetServerGroupResolver targetServerGroupResolver) {
    this.applySourceServerGroupSnapshotStage = applySourceServerGroupCapacityStage;
    this.targetServerGroupResolver = targetServerGroupResolver;
  }

  @Override
  public ImmutableList<StepDefinition> additionalSteps(StageExecution stage) {
    return ImmutableList.of(
        new StepDefinition(
            "snapshotSourceServerGroup", CaptureSourceServerGroupCapacityTask.class));
  }

  @Override
  public ImmutableList<StageDefinition> afterStageDefinitions(StageExecution stage) {
    return ImmutableList.of(
        new StageDefinition(
            "restoreMinCapacityFromSnapshot",
            applySourceServerGroupSnapshotStage,
            Collections.emptyMap()));
  }

  @Override
  public boolean supports(StageExecution stage) {
    StageData stageData = stage.mapTo(StageData.class);
    return stageData.getCloudProvider().equals("gce");
  }
}
