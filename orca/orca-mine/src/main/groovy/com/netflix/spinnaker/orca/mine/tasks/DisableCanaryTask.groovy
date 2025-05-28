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

package com.netflix.spinnaker.orca.mine.tasks

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import com.netflix.spinnaker.orca.mine.MineService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
@Slf4j
class DisableCanaryTask implements CloudProviderAware, Task {

  @Autowired MineService mineService
  @Autowired KatoService katoService

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    try {
      def canary = Retrofit2SyncCall.execute(mineService.getCanary(stage.context.canary.id))
      if (canary.health?.health == 'UNHEALTHY' || stage.context.unhealthy != null) {
        // If unhealthy, already disabled in MonitorCanaryTask
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
          unhealthy : true
        ]).build()
      }
    } catch (SpinnakerServerException e) {
      log.error("Exception occurred while getting canary status with id {} from mine, continuing with disable",
        stage.context.canary.id, e)
    }

    def selector = stage.context.containsKey('disabledCluster') ? 'baselineCluster' : 'canaryCluster'
    def ops = DeployedClustersUtil.toKatoAsgOperations('disableServerGroup', stage.context, selector)
    def dSG = DeployedClustersUtil.getDeployServerGroups(stage.context)

    log.info "Disabling ${selector} in ${stage.id} with ${ops}"
    String cloudProvider = ops && !ops.empty ? ops.first()?.values().first()?.cloudProvider : getCloudProvider(stage) ?: 'aws'
    def taskId = katoService.requestOperations(cloudProvider, ops)

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
      'kato.last.task.id'    : taskId,
      'deploy.server.groups' : dSG,
      disabledCluster        : selector
    ]).build()
  }
}
