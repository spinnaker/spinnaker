/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ApplySourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.CaptureSourceServerGroupCapacityStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.stereotype.Component

@Component
class RollbackServerGroupStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = "rollbackServerGroup"

  @Autowired
  AutowireCapableBeanFactory autowireCapableBeanFactory

  @Override
  def <T extends Execution<T>> List<Stage<T>> aroundStages(Stage<T> stage) {
    def stageData = stage.mapTo(StageData)

    if (!stageData.rollbackType) {
      throw new IllegalStateException("Missing `rollbackType` (execution: ${stage.execution.id})")
    }

    def explicitRollback = stage.mapTo("/rollbackContext", stageData.rollbackType.implementationClass) as Rollback
    autowireCapableBeanFactory.autowireBean(explicitRollback)
    return explicitRollback.buildStages(stage)
  }

  static enum RollbackType {
    EXPLICIT(ExplicitRollback)

    final Class implementationClass

    RollbackType(Class<? extends Rollback> implementationClass) {
      this.implementationClass = implementationClass
    }
  }

  static class StageData {
    RollbackType rollbackType
  }

  static interface Rollback {
    def <T extends Execution<T>> List<Stage<T>> buildStages(Stage<T> parentStage)
  }

  static class ExplicitRollback implements Rollback {
    String rollbackServerGroupName
    String restoreServerGroupName

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
    def <T extends Execution<T>> List<Stage<T>> buildStages(Stage<T> parentStage) {
      def stages = []

      Map enableServerGroupContext = new HashMap(parentStage.context)
      enableServerGroupContext.serverGroupName = restoreServerGroupName
      stages << newStage(
        parentStage.execution, enableServerGroupStage.type, "enable", enableServerGroupContext, parentStage, SyntheticStageOwner.STAGE_AFTER
      )

      stages << buildCaptureSourceServerGroupCapacityStage(parentStage, parentStage.mapTo(ResizeStrategy.Source))

      Map resizeServerGroupContext = new HashMap(parentStage.context) + [
        action            : ResizeStrategy.ResizeAction.scale_to_server_group.toString(),
        source            : {
          def source = parentStage.mapTo(ResizeStrategy.Source)
          source.serverGroupName = rollbackServerGroupName
          return source
        }.call(),
        asgName           : restoreServerGroupName,
        pinMinimumCapacity: true
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
        credentials: source.credentials,
        target     : [
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

}
