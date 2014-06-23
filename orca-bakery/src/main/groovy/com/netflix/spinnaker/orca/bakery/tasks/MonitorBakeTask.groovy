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
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
class MonitorBakeTask implements Task {

  @Autowired
  BakeryService bakery

  @Override
  TaskResult execute(TaskContext context) {
    def region = context.inputs.region as String
    def previousStatus = context.inputs."bake.status" as BakeStatus

    def newStatus = bakery.lookupStatus(region, previousStatus.id).toBlockingObservable().single()

    new DefaultTaskResult(mapStatus(newStatus), ["bake.status": newStatus])
  }

  private TaskResult.Status mapStatus(BakeStatus newStatus) {
    switch (newStatus.state) {
      case BakeStatus.State.COMPLETED:
        return TaskResult.Status.SUCCEEDED
      case BakeStatus.State.CANCELLED:
        return TaskResult.Status.FAILED
      default:
        return TaskResult.Status.RUNNING
    }
  }
}
