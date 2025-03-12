/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.bakery.BakerySelector
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Slf4j
@Component
@CompileStatic
class MonitorBakeTask implements OverridableTimeoutRetryableTask {

  long backoffPeriod = 30000

  @Value('${tasks.monitor-bake.timeout-millis:3600000}')
  long timeout

  @Autowired(required = false)
  BakerySelector bakerySelector

  @Autowired
  CreateBakeTask createBakeTask

  @Override
  TaskResult execute(StageExecution stage) {
    if (!bakerySelector) {
      throw new UnsupportedOperationException(
        "You have not enabled baking for this orca instance. Set bakery.enabled: true")
    }

    def region = stage.context.region as String
    def previousStatus = stage.context.status as BakeStatus

    try {
      def bakery = bakerySelector.select(stage)
      def newStatus = bakery.service.lookupStatus(region, previousStatus.id)
      if (isCanceled(newStatus.state) && previousStatus.state == BakeStatus.State.PENDING) {
        log.info("Original bake was 'canceled', re-baking (executionId: ${stage.execution.id}, previousStatus: ${previousStatus.state})")
        def rebakeResult = createBakeTask.execute(stage)
        return TaskResult.builder(ExecutionStatus.RUNNING).context(rebakeResult.context).outputs(rebakeResult.outputs).build()
      }

      TaskResult.builder(mapStatus(newStatus)).context([status: newStatus]).build()
    } catch (SpinnakerHttpException e) {
      log.error("Monitor Error {}", e.getMessage())
      if (e.responseCode == 404) {
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }
      throw e
    }
  }

  static boolean isCanceled(BakeStatus.State state) {
    return [BakeStatus.State.CANCELED, BakeStatus.State.CANCELLED].contains(state)
  }

  private static ExecutionStatus mapStatus(BakeStatus newStatus) {
    switch (newStatus.state) {
      case BakeStatus.State.COMPLETED:
        return newStatus.result == BakeStatus.Result.SUCCESS ? ExecutionStatus.SUCCEEDED : ExecutionStatus.TERMINAL
      // Rosco returns CANCELED.
      case BakeStatus.State.CANCELED:
        return ExecutionStatus.TERMINAL
      // Netflix's internal bakery returns CANCELLED.
      case BakeStatus.State.CANCELLED:
        return ExecutionStatus.TERMINAL
      default:
        return ExecutionStatus.RUNNING
    }
  }
}
