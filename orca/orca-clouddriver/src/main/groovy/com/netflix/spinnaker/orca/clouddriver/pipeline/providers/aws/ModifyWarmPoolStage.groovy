/*
 * Copyright 2026 McIntosh.farm
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
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.warmpool.DeleteWarmPoolTask
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.warmpool.UpsertWarmPoolTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class ModifyWarmPoolStage extends TargetServerGroupLinearStageSupport implements ForceCacheRefreshAware {

  public static final String TYPE = StageDefinitionBuilder.getType(ModifyWarmPoolStage)

  private final DynamicConfigService dynamicConfigService

  @Autowired
  ModifyWarmPoolStage(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService
  }

  @Override
  protected void taskGraphInternal(StageExecution stage, TaskNode.Builder builder) {
    def data = stage.mapTo(StageData)
    switch (data.action) {
      case StageAction.upsert:
        builder
          .withTask("upsertWarmPool", UpsertWarmPoolTask)
        break
      case StageAction.delete:
        builder
          .withTask("deleteWarmPool", DeleteWarmPoolTask)
        break
      default:
        throw new RuntimeException("No action specified!")
    }

    builder
      .withTask("monitor", MonitorKatoTask)

    if (isForceCacheRefreshEnabled(dynamicConfigService)) {
      builder.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    }

    builder.withTask("waitForWarmPool", WaitForWarmPool)
  }

  enum StageAction {
    upsert, delete
  }

  static class StageData {
    StageAction action
  }

  @Component
  static class WaitForWarmPool implements RetryableTask {
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

      def warmPoolConfiguration = targetServerGroup.get().getWarmPoolConfiguration()
      def isComplete = stageData.isDelete() ? !warmPoolConfiguration : warmPoolConfiguration != null

      return isComplete ? TaskResult.ofStatus(ExecutionStatus.SUCCEEDED) : TaskResult.ofStatus(ExecutionStatus.RUNNING)
    }

    static class StageData {
      String credentials
      String serverGroupName
      String asgName
      List<String> regions
      String region
      String action

      String getRegion() {
        return regions ? regions[0] : region
      }

      String getServerGroupName() {
        return serverGroupName ?: asgName
      }

      boolean isDelete() {
        return action == ModifyWarmPoolStage.StageAction.delete.toString()
      }
    }
  }
}
