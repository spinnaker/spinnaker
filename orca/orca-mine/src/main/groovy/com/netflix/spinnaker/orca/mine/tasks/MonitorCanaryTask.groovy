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
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import com.netflix.spinnaker.orca.mine.MineService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class MonitorCanaryTask implements CloudProviderAware, OverridableTimeoutRetryableTask {
  long backoffPeriod = TimeUnit.MINUTES.toMillis(1)
  long timeout = TimeUnit.DAYS.toMillis(2)

  private long timeoutBuffer = TimeUnit.MINUTES.toMillis(15)

  @Autowired
  MineService mineService
  @Autowired
  KatoService katoService

  @Override
  TaskResult execute(StageExecution stage) {
    Map context = stage.context
    // add a 15-minute buffer to accommodate backoffs, etc.
    Integer canaryDuration = Integer.parseInt((stage.context.canary.canaryConfig?.lifetimeHours ?: "2").toString())
    Long canaryDurationMillis = TimeUnit.HOURS.toMillis(canaryDuration) + timeoutBuffer
    Map outputs = [
      canary : context.canary,
      stageTimeoutMs: Math.max(canaryDurationMillis, timeout)
    ]

    try {
      outputs << [
        canary : Retrofit2SyncCall.execute(mineService.getCanary(context.canary.id))
      ]
    } catch (SpinnakerServerException e) {
      log.error("Exception occurred while getting canary with id ${context.canary.id} from mine service", e)
      return TaskResult.builder(ExecutionStatus.RUNNING).context(outputs).build()
    }

    if (outputs.canary.status?.complete) {
      log.info("Canary $stage.id complete")
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build()
    }

    if (outputs.canary.health?.health == 'UNHEALTHY' && !context.disableRequested) {
      log.info("Canary $stage.id unhealthy, disabling")
      def operations = DeployedClustersUtil.toKatoAsgOperations('disableServerGroup', stage.context)
      log.info "Disabling canary $stage.id with ${operations}"
      katoService.requestOperations(getCloudProvider(operations, stage), operations)
      outputs.disableRequested = true
    } else {
      outputs.disableRequested = false
    }

    Map scaleUp = context.scaleUp
    if (scaleUp && scaleUp.enabled && !scaleUp.complete) {
      int capacity = scaleUp.capacity as Integer
      if (System.currentTimeMillis() - outputs.canary.launchedDate > TimeUnit.MINUTES.toMillis(scaleUp.delay as Long)) {
        def resizeCapacity = [min: capacity, max: capacity, desired: capacity]
        def resizeOps = DeployedClustersUtil.toKatoAsgOperations('resizeServerGroup', stage.context).collect { it.resizeServerGroup.capacity = resizeCapacity; it  }
        outputs.scaleUp = scaleUp
        outputs.scaleUp.katoId = katoService.requestOperations(getCloudProvider(resizeOps, stage), resizeOps).id
        outputs.scaleUp.complete = true
        log.info("Canary $stage.id scale up requested")
      }
    }

    log.info("Canary in progress: ${outputs.canary}")
    return TaskResult.builder(ExecutionStatus.RUNNING).context(outputs).build()
  }

  String getCloudProvider(List<Map> operations, StageExecution stage){
    return operations && !operations.empty ? operations.first()?.values().first()?.cloudProvider : getCloudProvider(stage) ?: 'aws'
  }
}
