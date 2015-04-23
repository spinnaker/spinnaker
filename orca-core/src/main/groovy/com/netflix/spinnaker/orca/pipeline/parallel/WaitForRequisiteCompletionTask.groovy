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

import static com.netflix.spinnaker.orca.ExecutionStatus.*

@Component
@CompileStatic
class WaitForRequisiteCompletionTask implements RetryableTask {
  long backoffPeriod = 5000
  long timeout = 7200000

  @Override
  TaskResult execute(Stage stage) {
    boolean allRequisiteStagesAreComplete = true

    def requisiteIds = stage.context.requisiteIds as List<String>
    requisiteIds?.each { String requisiteId ->
      def requisiteStage = stage.execution.stages.find { it.refId == requisiteId }
      if (requisiteStage?.status != SUCCEEDED) {
        allRequisiteStagesAreComplete = false
      }

      def tasks = (requisiteStage?.tasks ?: []) as List<Task>
      if (tasks.find { it.status != SUCCEEDED }) {
        allRequisiteStagesAreComplete = false
      }
    }

    return new DefaultTaskResult(allRequisiteStagesAreComplete ? SUCCEEDED : RUNNING)
  }
}
