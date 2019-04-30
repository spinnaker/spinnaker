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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class CleanupCanaryTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired KatoService katoService

  @Override
  TaskResult execute(Stage stage) {
    def canary = stage.mapTo("/canary", Canary)
    if (canary.health?.health == Health.UNHEALTHY.health && !canary.canaryConfig?.actionsForUnhealthyCanary?.find {
      it.action == Action.TERMINATE
    }) {
      // should succeed (and not perform any cleanup) when the canary is unhealthy and not configured to terminate
      return TaskResult.SUCCEEDED
    }

    def ops = DeployedClustersUtil.toKatoAsgOperations('destroyServerGroup', stage.context)
    log.info "Cleaning up canary clusters in ${stage.id} with ${ops}"
    String cloudProvider = ops && !ops.empty ? ops.first()?.values().first()?.cloudProvider : getCloudProvider(stage) ?: 'aws'
    def taskId = katoService.requestOperations(cloudProvider, ops).toBlocking().first()
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(['kato.last.task.id': taskId]).build()
  }

  static class Canary {
    Health health
    CanaryConfig canaryConfig
  }

  @Canonical
  static class Health {
    public final static Health UNHEALTHY = new Health("UNHEALTHY")

    String health
  }

  static class CanaryConfig {
    List<CanaryAction> actionsForUnhealthyCanary
  }

  static class CanaryAction {
    Action action
    int delayBeforeActionInMins = 0
  }

  static enum Action {
    DISABLE, CANCEL, COMPLETE, FAIL, TERMINATE
  }
}
