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

package com.netflix.spinnaker.orca.mayo.tasks

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Slf4j
@Component
class MonitorPipelineTask implements RetryableTask {

  @Autowired
  ExecutionRepository executionRepository

  long backoffPeriod = 1000
  long timeout = TimeUnit.HOURS.toMillis(12)

  @Override
  TaskResult execute(Stage stage) {
    String pipelineId = stage.context.executionId
    Execution childPipeline = executionRepository.retrievePipeline(pipelineId)

    switch (childPipeline.status) {
      case ExecutionStatus.SUCCEEDED:
        return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [status: childPipeline.status])
        break
      case {
        ExecutionStatus.NOT_STARTED ||
          ExecutionStatus.RUNNING ||
          ExecutionStatus.SUSPENDED
      }:
        return new DefaultTaskResult(ExecutionStatus.RUNNING, [status: childPipeline.status])
        break
      default:
        return new DefaultTaskResult(ExecutionStatus.FAILED, [status: childPipeline.status])
        break
    }
  }
}
