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

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
@Slf4j
class MonitorAcaTaskTask extends AbstractCloudProviderAwareTask implements OverridableTimeoutRetryableTask {
  long backoffPeriod = 10000
  long timeout = TimeUnit.DAYS.toMillis(2)

  @Autowired
  MineService mineService

  @Override
  TaskResult execute(Stage stage) {
    Map context = stage.context
    Map outputs = [
      canary : context.canary
    ]

    try {
      outputs << [
        canary : mineService.getCanary(context.canary.id)
      ]
    } catch (RetrofitError e) {
      log.error("Exception occurred while getting canary with id ${context.canary.id} from mine service", e)
      return TaskResult.builder(ExecutionStatus.RUNNING).context(outputs).build()
    }

    if (outputs.canary.status?.complete) {
      log.info("Canary $stage.id complete")
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build()
    }

    log.info("Canary in progress: ${outputs.canary}")
    return TaskResult.builder(ExecutionStatus.RUNNING).context(outputs).build()
  }
}
