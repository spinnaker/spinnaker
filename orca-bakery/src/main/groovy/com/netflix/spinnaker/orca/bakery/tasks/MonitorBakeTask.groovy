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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
@CompileStatic
class MonitorBakeTask implements OverridableTimeoutRetryableTask {

  long backoffPeriod = 30000
  long timeout = 3600000 // 1hr

  @Autowired
  BakeryService bakery

  @Autowired
  CreateBakeTask createBakeTask

  @Override
  TaskResult execute(Stage stage) {
    def region = stage.context.region as String
    def previousStatus = stage.context.status as BakeStatus

    try {
      def newStatus = bakery.lookupStatus(region, previousStatus.id).toBlocking().single()
      if (isCanceled(newStatus.state) && previousStatus.state == BakeStatus.State.PENDING) {
        log.info("Original bake was 'canceled', re-baking (executionId: ${stage.execution.id}, previousStatus: ${previousStatus.state})")
        def rebakeResult = createBakeTask.execute(stage)
        return new TaskResult(ExecutionStatus.RUNNING, rebakeResult.context, rebakeResult.outputs)
      }

      new TaskResult(mapStatus(newStatus), [status: newStatus])
    } catch (RetrofitError e) {
      if (e.response?.status == 404) {
        return new TaskResult(ExecutionStatus.RUNNING)
      }
      throw e
    }
  }

  static boolean isCanceled(BakeStatus.State state) {
    return [BakeStatus.State.CANCELED, BakeStatus.State.CANCELLED].contains(state)
  }

  private ExecutionStatus mapStatus(BakeStatus newStatus) {
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
