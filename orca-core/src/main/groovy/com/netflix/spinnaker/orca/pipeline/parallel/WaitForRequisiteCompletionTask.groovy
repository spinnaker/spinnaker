/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.parallel

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.orca.ExecutionStatus.*

@Component
@CompileStatic
class WaitForRequisiteCompletionTask implements RetryableTask {
  long backoffPeriod = 5000
  long timeout = TimeUnit.DAYS.toMillis(1)

  @Override
  TaskResult execute(Stage stage) {
    boolean allRequisiteStagesAreComplete = true
    Set<String> terminalStageNames = []

    def requisiteIds = stage.context.requisiteIds as List<String>
    requisiteIds?.each { String requisiteId ->
      def requisiteStage = stage.execution.stages.find { it.refId == requisiteId }
      if (!requisiteStage) {
        allRequisiteStagesAreComplete = false
        return
      }

      def requisiteStages = [requisiteStage] + stage.execution.stages.findAll { it.parentStageId == requisiteStage.id }
      requisiteStages.each {
        if ( !(it.status in [SUCCEEDED, FAILED_CONTINUE]) ) {
          allRequisiteStagesAreComplete = false
        }
        if (it.status == TERMINAL) {
          terminalStageNames << it?.name
        }

        def tasks = (it.tasks ?: []) as List<Task>
        if (tasks && !(tasks[-1].status in [SUCCEEDED, FAILED_CONTINUE]) ) {
          // ensure the last task has completed (heuristic for all tasks being complete)
          allRequisiteStagesAreComplete = false
        }
      }
    }

    if (terminalStageNames) {
      throw new IllegalStateException("Requisite stage failures: ${terminalStageNames.join(',')}")
    }

    return new DefaultTaskResult(allRequisiteStagesAreComplete ? SUCCEEDED : RUNNING)
  }
}
