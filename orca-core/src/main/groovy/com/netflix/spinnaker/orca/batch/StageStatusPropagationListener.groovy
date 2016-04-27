/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.StepExecution
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
@Slf4j
class StageStatusPropagationListener extends AbstractStagePropagationListener {

  @Autowired
  StageStatusPropagationListener(ExecutionRepository executionRepository) {
    super(executionRepository)
  }

  @Override
  void beforeTask(Stage stage, StepExecution stepExecution) {
    if (stage.status?.complete) {
      return
    }

    stage.startTime = stage.startTime ?: System.currentTimeMillis()
    stage.status = ExecutionStatus.RUNNING
    saveStage stage
  }

  @Override
  void afterTask(Stage stage, StepExecution stepExecution) {
    def orcaTaskStatus = stepExecution.executionContext.get("orcaTaskStatus") as ExecutionStatus
    if (orcaTaskStatus) {
      def nonBookendTasks = stage.tasks.findAll { !DefaultTask.isBookend(it) }
      if (orcaTaskStatus == ExecutionStatus.SUCCEEDED && (nonBookendTasks && nonBookendTasks[-1].status != ExecutionStatus.SUCCEEDED)) {
        // mark stage as RUNNING as not all tasks have completed
        stage.status = ExecutionStatus.RUNNING
        for (Task task : nonBookendTasks) {
          if (task.status == ExecutionStatus.FAILED_CONTINUE) {
            // task fails and continue pipeline on failure is checked, set stage to the same status.
            stage.status = ExecutionStatus.FAILED_CONTINUE
          }
        }
      } else {
        stage.status = orcaTaskStatus

        if (orcaTaskStatus.complete) {
          stage.endTime = stage.endTime ?: System.currentTimeMillis()
        }
      }
    } else {
      stage.endTime = System.currentTimeMillis()
      stage.status = ExecutionStatus.TERMINAL
    }
    saveStage stage
  }
}
