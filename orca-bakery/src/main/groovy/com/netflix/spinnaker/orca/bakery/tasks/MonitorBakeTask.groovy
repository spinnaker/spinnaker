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

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class MonitorBakeTask implements RetryableTask {

  long backoffPeriod = 1000
  long timeout = 600000

  @Autowired BakeryService bakery

  @Override
  TaskResult execute(Stage stage) {
    def region = stage.context.region as String
    def previousStatus = stage.context.status as BakeStatus

    // TODO: could skip the lookup if it's already complete as it will be for a previously requested bake

    def newStatus = bakery.lookupStatus(region, previousStatus.id).toBlocking().single()

    new DefaultTaskResult(mapStatus(newStatus), [status: newStatus])
  }

  private ExecutionStatus mapStatus(BakeStatus newStatus) {
    switch (newStatus.state) {
      case BakeStatus.State.COMPLETED:
        return newStatus.result == BakeStatus.Result.SUCCESS ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED
      case BakeStatus.State.CANCELLED:
        return ExecutionStatus.FAILED
      default:
        return ExecutionStatus.RUNNING
    }
  }
}
