/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DisableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.EnableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.newStage

class ExplicitRollback implements Rollback {
  String rollbackServerGroupName
  String restoreServerGroupName
  Integer targetHealthyRollbackPercentage

  @Autowired
  @JsonIgnore
  EnableServerGroupStage enableServerGroupStage

  @Autowired
  @JsonIgnore
  DisableServerGroupStage disableServerGroupStage

  @Autowired
  @JsonIgnore
  ResizeServerGroupStage resizeServerGroupStage

  @Autowired
  @JsonIgnore
  CaptureSourceServerGroupCapacityStage captureSourceServerGroupCapacityStage

  @Autowired
  @JsonIgnore
  ApplySourceServerGroupCapacityStage applySourceServerGroupCapacityStage

  @JsonIgnore
  def List<Stage> buildStages(Stage parentStage) {
    def stages = []

    Map enableServerGroupContext = new HashMap(parentStage.context)
    enableServerGroupContext.targetHealthyDeployPercentage = targetHealthyRollbackPercentage
    enableServerGroupContext.serverGroupName = restoreServerGroupName
    stages << newStage(
      parentStage.execution, enableServerGroupStage.type, "enable", enableServerGroupContext, parentStage, SyntheticStageOwner.STAGE_AFTER
    )

    stages << buildCaptureSourceServerGroupCapacityStage(parentStage, parentStage.mapTo(ResizeStrategy.Source))

    Map resizeServerGroupContext = new HashMap(parentStage.context) + [
      action                       : ResizeStrategy.ResizeAction.scale_to_server_group.toString(),
      source                       : {
        def source = parentStage.mapTo(ResizeStrategy.Source)
        source.serverGroupName = rollbackServerGroupName
        return source
      }.call(),
      asgName                      : restoreServerGroupName,
      pinMinimumCapacity           : true,
      targetHealthyDeployPercentage: targetHealthyRollbackPercentage
    ]
    stages << newStage(
      parentStage.execution, resizeServerGroupStage.type, "resize", resizeServerGroupContext, parentStage, SyntheticStageOwner.STAGE_AFTER
    )

    Map disableServerGroupContext = new HashMap(parentStage.context)
    disableServerGroupContext.serverGroupName = rollbackServerGroupName
    stages << newStage(
      parentStage.execution, disableServerGroupStage.type, "disable", disableServerGroupContext, parentStage, SyntheticStageOwner.STAGE_AFTER
    )

    stages << buildApplySourceServerGroupCapacityStage(parentStage, parentStage.mapTo(ResizeStrategy.Source))
    return stages
  }

  Stage buildCaptureSourceServerGroupCapacityStage(Stage parentStage,
                                                   ResizeStrategy.Source source) {
    Map captureSourceServerGroupCapacityContext = [
      useSourceCapacity: true,
      source           : [
        asgName        : rollbackServerGroupName,
        serverGroupName: rollbackServerGroupName,
        region         : source.region,
        account        : source.credentials,
        cloudProvider  : source.cloudProvider
      ]
    ]
    return newStage(
      parentStage.execution,
      captureSourceServerGroupCapacityStage.type,
      "snapshot",
      captureSourceServerGroupCapacityContext,
      parentStage,
      SyntheticStageOwner.STAGE_AFTER
    )
  }

  Stage buildApplySourceServerGroupCapacityStage(Stage parentStage,
                                                 ResizeStrategy.Source source) {
    Map applySourceServerGroupCapacityContext = [
      credentials  : source.credentials,
      cloudProvider: source.cloudProvider,
      target       : [
        asgName        : restoreServerGroupName,
        serverGroupName: restoreServerGroupName,
        region         : source.region,
        account        : source.credentials,
        cloudProvider  : source.cloudProvider
      ]
    ]
    return newStage(
      parentStage.execution,
      applySourceServerGroupCapacityStage.type,
      "apply",
      applySourceServerGroupCapacityContext,
      parentStage,
      SyntheticStageOwner.STAGE_AFTER
    )
  }
}
