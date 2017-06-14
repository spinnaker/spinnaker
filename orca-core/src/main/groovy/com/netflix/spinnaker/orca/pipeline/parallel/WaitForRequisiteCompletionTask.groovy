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

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.ExecutionStatus.*

@Deprecated
@Component
@Slf4j
@CompileStatic
class WaitForRequisiteCompletionTask implements RetryableTask {
  static final Set<ExecutionStatus> COMPLETED_STATUS = [SUCCEEDED, FAILED_CONTINUE, SKIPPED, STOPPED].toSet().asImmutable()

  long backoffPeriod = 5000
  long timeout = TimeUnit.DAYS.toMillis(1)

  @Override
  TaskResult execute(Stage stage) {
    boolean allRequisiteStagesAreComplete = true
    Set<String> terminalStageNames = []
    Set<String> stoppedStageNames = []

    def requisiteIds = stage.context.requisiteIds as List<String>
    requisiteIds?.each { String requisiteId ->
      def requisiteStage = stage.execution.stages.find { it.refId == requisiteId }
      if (!requisiteStage) {
        allRequisiteStagesAreComplete = false
        return
      }

      def requisiteStages = [requisiteStage] + stage.execution.stages.findAll { it.parentStageId == requisiteStage.id }
      requisiteStages.each {
        if ( !(it.status in COMPLETED_STATUS) ) {
          allRequisiteStagesAreComplete = false
        }
        if (it.status == TERMINAL) {
          terminalStageNames << it?.name
        }
        if (it.status == STOPPED) {
          stoppedStageNames << it?.name
        } else {
          def tasks = (it.tasks ?: []) as List<Task>
          if (tasks && !(tasks[-1].status in COMPLETED_STATUS)) {
            // ensure the last task has completed (heuristic for all tasks being complete)
            allRequisiteStagesAreComplete = false
          }
        }
      }
    }

    if (terminalStageNames) {
      throw new IllegalStateException("Requisite stage failures: ${terminalStageNames.join(',')}")
    }

    // we don't want to fail this join point on a STOPPED upstream until all upstreams are in a completed state.
    //  STOPPED shouldn't fail execution of the pipeline
    if (allRequisiteStagesAreComplete && stoppedStageNames) {
      return new TaskResult(STOPPED)
    }

    return new TaskResult(allRequisiteStagesAreComplete ? SUCCEEDED : RUNNING)
  }
}
