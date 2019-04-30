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

package com.netflix.spinnaker.orca.front50.tasks

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

@Slf4j
@Component
class MonitorPipelineTask implements OverridableTimeoutRetryableTask {

  @Autowired
  ExecutionRepository executionRepository

  long backoffPeriod = TimeUnit.SECONDS.toMillis(15)
  long timeout = TimeUnit.HOURS.toMillis(12)

  @Override
  TaskResult execute(Stage stage) {
    String pipelineId = stage.context.executionId
    Execution childPipeline = executionRepository.retrieve(PIPELINE, pipelineId)

    if (childPipeline.status == ExecutionStatus.SUCCEEDED) {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([status: childPipeline.status]).outputs(childPipeline.getContext()).build()
    }

    if (childPipeline.status.halt) {
      // indicates a failure of some sort
      def terminalStages = childPipeline.stages.findAll { s -> s.status == ExecutionStatus.TERMINAL }
      List<String> errors = terminalStages
        .findResults { s ->
          if (s.context["exception"]?.details) {
            return [(s.context["exception"].details.errors ?: s.context["exception"].details.error)]
              .flatten()
              .collect {e -> buildExceptionMessage(childPipeline.name, e, s)}
          }
          if (s.context["kato.tasks"]) {
            return s.context["kato.tasks"]
              .findAll { k -> k.status?.failed }
              .findResults { k ->
                String message = k.exception?.message ?: k.history ? ((List<String>) k.history).last() : null
                return message ? buildExceptionMessage(childPipeline.name, message, s) : null
              }
          }
        }
        .flatten()

      def exceptionDetails = [:]
      if (errors) {
        exceptionDetails.details = [
            errors: errors
        ]
      }

      def haltingStage = terminalStages.find { it.status.halt }
      if (haltingStage) {
        exceptionDetails.source = [
          executionId: childPipeline.id,
          stageId    : haltingStage.id,
          stageName  : haltingStage.name,
          stageIndex : childPipeline.stages.indexOf(haltingStage)
        ]
      }

      return TaskResult.builder(ExecutionStatus.TERMINAL).context([
        status   : childPipeline.status,
        exception: exceptionDetails
      ]).build()
    }

    return TaskResult.builder(ExecutionStatus.RUNNING).context([status: childPipeline.status]).build()
  }

  private static String buildExceptionMessage(String pipelineName, String message, Stage stage) {
    "Exception in child pipeline stage (${pipelineName}: ${stage.name ?: stage.type}): ${message}"
  }
}
