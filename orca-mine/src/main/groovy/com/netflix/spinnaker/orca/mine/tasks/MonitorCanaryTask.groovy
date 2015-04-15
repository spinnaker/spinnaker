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
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.mine.Canary
import com.netflix.spinnaker.orca.mine.Cluster
import com.netflix.spinnaker.orca.mine.Health
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
  @Autowired
  ResultSerializationHelper resultSerializationHelper

  @Override
  TaskResult execute(Stage stage) {
    Map context = stage.context

    Canary canary = stage.mapTo('/canary', Canary)
    log.info("MonitorCanary, current status: ${canary}")

    def outputs = [canary: canary]
    outputs.canary = mineService.checkCanaryStatus(canary.id)
    if (outputs.canary.status.complete) {
      log.info("Canary complete")
      return resultSerializationHelper.result(ExecutionStatus.SUCCEEDED, [:], outputs)
    }

    if (outputs.canary.health?.health == Health.UNHEALTHY) {
      log.info("Canary unhealthy, disabling")
      String operation = 'disableAsgDescription'
      def operations = []
      for (d in canary.canaryDeployments) {
        for (c in [d.baselineCluster, d.canaryCluster]) {
          operations << [(operation): [asgName: c.name, regions: [c.region], credentials: c.accountName]]
        }
      }
      log.info "Calling ${operation} with ${operations}"
      katoService.requestOperations(operations).toBlocking().first()

      outputs.canary = mineService.disableCanaryAndScheduleForTermination(canary.id, "Canary is unhealthy")
    }

    Map scaleUp = context.scaleUp
    if (scaleUp && scaleUp.enabled && !scaleUp.complete) {
      int capacity = scaleUp.capacity as Integer
      if (System.currentTimeMillis() - canary.launchedDate > TimeUnit.MINUTES.toMillis(scaleUp.delay as Long)) {
        def resizeOps = []
        for (deployment in canary.canaryDeployments) {
          for (Cluster cluster in [deployment.canaryCluster, deployment.baselineCluster]) {
            resizeOps << [ resizeAsgDescription: [
              asgName: cluster.name,
              regions: [cluster.region],
              capacity: [min: capacity, max: capacity, desired: capacity],
              credentials: cluster.accountName]
            ]
          }
        }
        outputs.scaleUp = scaleUp
        outputs.scaleUp.katoId = katoService.requestOperations(resizeOps).toBlocking().first().id
        outputs.scaleUp.complete = true
        log.info('Canary scale up requested')
      }
    }

    log.info("Canary in progress: ${outputs.canary}")
    return resultSerializationHelper.result(ExecutionStatus.RUNNING, outputs)
  }
}
