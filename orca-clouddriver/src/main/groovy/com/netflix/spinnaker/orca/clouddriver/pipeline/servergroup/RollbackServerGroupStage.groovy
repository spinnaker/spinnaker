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
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeStrategy
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.stereotype.Component

@Component
class RollbackServerGroupStage extends LinearStage {
  public static final String PIPELINE_CONFIG_TYPE = "rollbackServerGroup"

  @Autowired
  AutowireCapableBeanFactory autowireCapableBeanFactory

  RollbackServerGroupStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    def stageData = stage.mapTo(StageData)

    if (!stageData.rollbackType) {
      throw new IllegalStateException("Missing `rollbackType` (execution: ${stage.execution.id})")
    }

    def explicitRollback = stage.mapTo("/rollbackContext", stageData.rollbackType.implementationClass) as Rollback
    autowireCapableBeanFactory.autowireBean(explicitRollback)
    return explicitRollback.buildSteps(stage)
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
    List<Step> buildSteps(Stage stage)
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

    @JsonIgnore
    List<Step> buildSteps(Stage stage) {
      Map enableServerGroupContext = new HashMap(stage.context)
      enableServerGroupContext.serverGroupName = restoreServerGroupName
      injectAfter(stage, "enable", enableServerGroupStage, enableServerGroupContext)

      Map resizeServerGroupContext = new HashMap(stage.context) + [
        action : ResizeStrategy.ResizeAction.scale_to_server_group.toString(),
        source : {
          def source = stage.mapTo(ResizeStrategy.Source)
          source.serverGroupName = rollbackServerGroupName
          return source
        }.call(),
        asgName: restoreServerGroupName
      ]
      injectAfter(stage, "resize", resizeServerGroupStage, resizeServerGroupContext)

      Map disableServerGroupContext = new HashMap(stage.context)
      disableServerGroupContext.serverGroupName = rollbackServerGroupName
      injectAfter(stage, "disable", disableServerGroupStage, disableServerGroupContext)

      return []
    }
  }
}
