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

package com.netflix.spinnaker.orca.tide.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.tide.TideService
import com.netflix.spinnaker.orca.tide.model.TideTask
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class WaitForTideTask implements RetryableTask {
  long backoffPeriod = 1000
  long timeout = 30 * 60 * 1000

  @Autowired
  TideService tideService

  @Override
  TaskResult execute(Stage stage) {
    String taskId = stage.context."tide.task.id"

    TideTask tideTask = tideService.getTask(taskId)

    ExecutionStatus status = tideTask.taskComplete ? ExecutionStatus.SUCCEEDED : ExecutionStatus.RUNNING
    Map outputs = [
        "tide.task": tideTask
    ]

    return new DefaultTaskResult(status, outputs)
  }
}
