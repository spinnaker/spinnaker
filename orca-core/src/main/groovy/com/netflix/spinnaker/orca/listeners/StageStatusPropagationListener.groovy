/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.listeners

import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task

@Slf4j
class StageStatusPropagationListener implements StageListener {

  @Override
  void beforeTask(Persister persister, Stage stage, Task task) {
    if (stage.status?.complete) {
      return
    }

    log.debug("***** $stage.execution.id Stage ${stage.type} starting")
    log.info("Marking Stage as RUNNING (stageId: ${stage.id})")
    stage.startTime = stage.startTime ?: System.currentTimeMillis()
    stage.status = ExecutionStatus.RUNNING

    if (stage.execution.executionEngine == "v2") {
      stage.context.stageDetails = [
        name       : stage.name,
        type       : stage.type,
        startTime  : stage.startTime,
        isSynthetic: stage.syntheticStageOwner != null
      ]
    }

    persister.save(stage)
  }

  @Override
  void afterTask(Persister persister, Stage stage, Task task, ExecutionStatus executionStatus, boolean wasSuccessful) {
    if (executionStatus) {
      def nonBookendTasks = stage.tasks.findAll { !it.bookend }
      def lastTask = task.stageEnd || task.name == "stageEnd"
      if (stage.syntheticStageOwner && task == nonBookendTasks[-1]) {
        // synthetic stages do not have stageStart/stageEnd
        // TODO-AJ only needed for v1 support
        lastTask = true
      }

      if (executionStatus == ExecutionStatus.SUCCEEDED && !lastTask) {
        // mark stage as RUNNING as not all tasks have completed
        stage.status = ExecutionStatus.RUNNING
        log.info("Task SUCCEEDED but not all other tasks are complete (stageId: ${stage.id})")
        for (Task t : nonBookendTasks) {
          if (t.status == ExecutionStatus.FAILED_CONTINUE) {
            // task fails and continue pipeline on failure is checked, set stage to the same status.
            stage.status = ExecutionStatus.FAILED_CONTINUE
          }
        }
      } else {
        stage.status = executionStatus

        if (executionStatus.complete) {
          log.debug("***** $stage.execution.id Stage $stage.type $executionStatus")
          stage.endTime = stage.endTime ?: System.currentTimeMillis()
        }
      }
    } else {
      log.debug("***** $stage.execution.id Stage $stage.type terminal due to missing status")
      stage.endTime = System.currentTimeMillis()
      stage.status = ExecutionStatus.TERMINAL
    }

    if (stage.endTime && stage.execution.executionEngine == "v2") {
      stage.context.stageDetails.endTime = stage.endTime
    }

    persister.save(stage)
  }
}
