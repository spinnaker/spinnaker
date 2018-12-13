/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ModifyAwsScalingProcessStage
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceLinearStageSupport
import com.netflix.spinnaker.orca.kato.tasks.scalingprocess.ResumeScalingProcessTask
import com.netflix.spinnaker.orca.kato.tasks.scalingprocess.SuspendScalingProcessTask
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
@Deprecated
class ModifyScalingProcessStage extends TargetReferenceLinearStageSupport {

  private final DynamicConfigService dynamicConfigService

  @Autowired
  ModifyScalingProcessStage(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService
  }

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    def data = stage.mapTo(StageData)
    switch (data.action) {
      case StageAction.suspend:
        builder
          .withTask("suspend", SuspendScalingProcessTask)
        break
      case StageAction.resume:
        builder
          .withTask("resume", ResumeScalingProcessTask)
        break
      default:
        throw new RuntimeException("No action specified!")
    }
    builder
      .withTask("monitor", MonitorKatoTask)

    if (isForceCacheRefreshEnabled(dynamicConfigService)) {
      builder.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    }

    builder.withTask("waitForScalingProcesses", ModifyAwsScalingProcessStage.WaitForScalingProcess)
  }

  enum StageAction {
    suspend, resume
  }

  static class StageData {
    StageAction action
  }
}
