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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.ForceCacheRefreshAware
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.scalingprocess.ResumeAwsScalingProcessTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.scalingprocess.SuspendAwsScalingProcessTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class ModifyAwsScalingProcessStage extends TargetServerGroupLinearStageSupport implements ForceCacheRefreshAware {

  public static final String TYPE = getType(ModifyAwsScalingProcessStage)

  private final DynamicConfigService dynamicConfigService

  @Autowired
  ModifyAwsScalingProcessStage(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService
  }

  @Override
  protected void taskGraphInternal(StageExecution stage, TaskNode.Builder builder) {
    def data = stage.mapTo(StageData)
    switch (data.action) {
      case StageAction.suspend:
        builder
          .withTask("suspend", SuspendAwsScalingProcessTask)
        break
      case StageAction.resume:
        builder
          .withTask("resume", ResumeAwsScalingProcessTask)
        break
      default:
        throw new RuntimeException("No action specified!")
    }

    builder
      .withTask("monitor", MonitorKatoTask)

    if (isForceCacheRefreshEnabled(dynamicConfigService)) {
      builder.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    }

    builder.withTask("waitForScalingProcesses", WaitForScalingProcess)
  }

  enum StageAction {
    suspend, resume
  }

  static class StageData {
    StageAction action
  }

  @Component
  static class WaitForScalingProcess implements RetryableTask {
    long timeout = 1200000
    long backoffPeriod = 20000

    @Autowired
    CloudDriverService cloudDriverService

    @Override
    TaskResult execute(StageExecution stage) {
      def stageData = stage.mapTo(StageData)
      def targetServerGroup = cloudDriverService.getTargetServerGroup(
          stageData.credentials, stageData.serverGroupName, stageData.region)

      if (!targetServerGroup.present) {
        throw new IllegalStateException("No server group found (serverGroupName: ${stageData.region}:${stageData.serverGroupName})")
      }

      def suspendedProcesses = targetServerGroup.get().getSuspendedProcesses()

      def isComplete
      if (stageData.isResume()) {
        isComplete = suspendedProcesses?.intersect(stageData.processes)?.isEmpty()
      } else {
        isComplete = suspendedProcesses?.intersect(stageData.processes) == stageData.processes
      }

      return isComplete ? TaskResult.ofStatus(ExecutionStatus.SUCCEEDED) : TaskResult.ofStatus(ExecutionStatus.RUNNING)
    }

    static class StageData {
      String credentials
      String serverGroupName
      String asgName
      List<String> regions
      String region
      String action
      List<String> processes

      String getRegion() {
        return regions ? regions[0] : region
      }

      String getServerGroupName() {
        return serverGroupName ?: asgName
      }

      boolean isResume() {
        return action == ModifyAwsScalingProcessStage.StageAction.resume.toString()
      }
    }
  }
}
