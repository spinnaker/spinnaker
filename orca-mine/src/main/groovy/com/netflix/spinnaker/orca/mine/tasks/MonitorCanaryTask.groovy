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

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Component
@Slf4j
class MonitorCanaryTask implements RetryableTask {

  long backoffPeriod = 5000
  long timeout = TimeUnit.DAYS.toMillis(2)

  @Autowired
  MineService mineService
  @Autowired
  KatoService katoService

  @Override
  TaskResult execute(Stage stage) {
    Map context = stage.context

    Map canary = context.canary
    log.info("MonitorCanary, current status: ${canary}")

    def outputs = [canary: canary]
    outputs.canary = mineService.checkCanaryStatus(canary.id)
    if (outputs.canary.status.complete) {
      log.info("Canary complete")
      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [:], [canary: outputs.canary])
    }

    Map scaleUp = context.scaleUp
    if (scaleUp && scaleUp.enabled && !scaleUp.complete) {
      int capacity = scaleUp.capacity as Integer
      if (System.currentTimeMillis() - canary.launchedDate > TimeUnit.MINUTES.toMillis(scaleUp.delay as Long)) {
        def resizeOps = []
        for (deployment in canary.canaryDeployments) {
          for (Map cluster in [deployment.canaryCluster, deployment.baselineCluster]) {
            def asg = context.'deploy.server.groups'[cluster.region].find { it.startsWith(cluster.name) }
            resizeOps << [resizeAsgDescription: [asgName: asg, regions: [cluster.region], capacity: [min: capacity, max: capacity, desired: capacity], credentials: cluster.accountName]]
          }
        }
        outputs.scaleUp = scaleUp
        outputs.scaleUp.katoId = katoService.requestOperations(resizeOps).toBlocking().first().id
        outputs.scaleUp.complete = true
        log.info('Canary scale up requested')
      }
    }

    log.info("Canary in progress: ${outputs.canary}")
    return new DefaultTaskResult(ExecutionStatus.RUNNING, outputs)
  }
}
