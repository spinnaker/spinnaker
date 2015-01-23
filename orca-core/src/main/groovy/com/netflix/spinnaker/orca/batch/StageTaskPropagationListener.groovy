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

package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import java.util.regex.Pattern
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.StepExecution
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class StageTaskPropagationListener extends AbstractStagePropagationListener {

  private final Pattern TASK_NAME_PATTERN = ~/(?<=[\.])(\S+)(?=\.)/

  @Autowired
  StageTaskPropagationListener(ExecutionRepository executionRepository) {
    super(executionRepository)
  }

  @Override
  void beforeTask(Stage stage, StepExecution stepExecution) {
    def taskName = taskName(stepExecution)
    def task = stage.tasks.find { it.name == taskName }
    if (!task) {
      task = new DefaultTask(name: taskName, status: ExecutionStatus.RUNNING)
      stage.tasks << task
      saveStage stage
    }
  }

  @Override
  void afterTask(Stage stage, StepExecution stepExecution) {
    def taskName = taskName(stepExecution)
    def task = stage.tasks.find { it.name == taskName }
    ExecutionStatus executionStatus = mapBatchStatus(stepExecution.status)
    task.status = executionStatus
    task.endTime = System.currentTimeMillis()
    saveStage stage
  }

  static ExecutionStatus mapBatchStatus(BatchStatus status) {
    switch (status) {
      case BatchStatus.FAILED:
      case BatchStatus.ABANDONED:
        return ExecutionStatus.FAILED
      case BatchStatus.STARTING:
      case BatchStatus.STARTED:
        return ExecutionStatus.RUNNING
      case BatchStatus.STOPPED:
        return ExecutionStatus.SUSPENDED
      case BatchStatus.COMPLETED:
        return ExecutionStatus.SUCCEEDED
      default:
        return ExecutionStatus.RUNNING
    }
  }
}
