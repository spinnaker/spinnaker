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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import static com.netflix.spinnaker.orca.mine.pipeline.CanaryStage.DEFAULT_CLUSTER_DISABLE_WAIT_TIME

@Component
@Slf4j
class DisableCanaryTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired MineService mineService
  @Autowired KatoService katoService

  @Override
  TaskResult execute(Stage stage) {

    Integer waitTime = stage.context.clusterDisableWaitTime != null ? stage.context.clusterDisableWaitTime : DEFAULT_CLUSTER_DISABLE_WAIT_TIME

    try {
      def canary = mineService.getCanary(stage.context.canary.id)
      if (canary.health?.health == 'UNHEALTHY' || stage.context.unhealthy != null) {
        // If unhealthy, already disabled in MonitorCanaryTask
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
          waitTime  : waitTime,
          unhealthy : true
        ]).build()
      }
    } catch (RetrofitError e) {
      log.error("Exception occurred while getting canary status with id {} from mine, continuing with disable",
        stage.context.canary.id, e)
    }

    def selector = stage.context.containsKey('disabledCluster') ? 'baselineCluster' : 'canaryCluster'
    def ops = DeployedClustersUtil.toKatoAsgOperations('disableServerGroup', stage.context, selector)
    def dSG = DeployedClustersUtil.getDeployServerGroups(stage.context)

    log.info "Disabling ${selector} in ${stage.id} with ${ops}"
    String cloudProvider = ops && !ops.empty ? ops.first()?.values().first()?.cloudProvider : getCloudProvider(stage) ?: 'aws'
    def taskId = katoService.requestOperations(cloudProvider, ops).toBlocking().first()

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
      'kato.last.task.id'    : taskId,
      'deploy.server.groups' : dSG,
      disabledCluster        : selector,
      waitTime               : waitTime
    ]).build()
  }
}
