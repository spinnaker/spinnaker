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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.front50.pipeline.MonitorPipelineStage

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE

@Slf4j
@Component
class MonitorPipelineTask implements OverridableTimeoutRetryableTask {

  ExecutionRepository executionRepository
  ObjectMapper objectMapper

  long backoffPeriod = TimeUnit.SECONDS.toMillis(15)
  long timeout = TimeUnit.HOURS.toMillis(12)

  @Autowired
  public MonitorPipelineTask(ExecutionRepository executionRepository, ObjectMapper objectMapper) {
    this.executionRepository = executionRepository
    this.objectMapper = objectMapper
  }

  @Override
  TaskResult execute(StageExecution stage) {
    List<String> pipelineIds
    boolean isLegacyStage = false
    MonitorPipelineStage.StageParameters stageData = stage.mapTo(MonitorPipelineStage.StageParameters.class)

    if (stage.type == MonitorPipelineStage.PIPELINE_CONFIG_TYPE) {
      pipelineIds = stageData.executionIds
    } else {
      pipelineIds = Collections.singletonList(stageData.executionId)
      isLegacyStage = true
    }

    HashMap<String, MonitorPipelineStage.ChildPipelineStatusDetails> pipelineStatuses = new HashMap<>(pipelineIds.size())
    PipelineExecution firstPipeline

    pipelineIds.forEach { pipelineId ->
      PipelineExecution childPipeline = executionRepository.retrieve(PIPELINE, pipelineId)
      if (firstPipeline == null) {
        // Capture the first pipeline, since if there is only one, we will return its context as part of TaskResult
        firstPipeline = childPipeline
      }
      MonitorPipelineStage.ChildPipelineStatusDetails details = new MonitorPipelineStage.ChildPipelineStatusDetails()
      details.status = childPipeline.status
      details.application = childPipeline.application
      pipelineStatuses.put(pipelineId, details)

      if (childPipeline.status.halt) {
        details.exception = new MonitorPipelineStage.ChildPipelineException()

        // indicates a failure of some sort
        def terminalStages = childPipeline.stages.findAll { s -> s.status == ExecutionStatus.TERMINAL }
        List<String> errors = terminalStages
            .findResults { s ->
              if (s.context["exception"]?.details) {
                return [(s.context["exception"].details.errors ?: s.context["exception"].details.error)]
                    .flatten()
                    .collect { e -> buildExceptionMessage(childPipeline.name, e as String, s) }
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

        details.exception.details.errors = errors

        def haltingStage = terminalStages.find { it.status.halt }
        if (haltingStage) {
          details.exception.source.executionId = childPipeline.id
          details.exception.source.stageId = haltingStage.id
          details.exception.source.stageName = haltingStage.name
          details.exception.source.stageIndex = childPipeline.stages.indexOf(haltingStage)
        }
      }
    }

    boolean allPipelinesSucceeded = pipelineStatuses.every { it.value.status == ExecutionStatus.SUCCEEDED }
    boolean allPipelinesCompleted = pipelineStatuses.every { it.value.status.complete }
    boolean anyPipelinesFailed = pipelineStatuses.any { effectiveStatus(it.value.status) == ExecutionStatus.TERMINAL }

    MonitorPipelineStage.StageResult result = new MonitorPipelineStage.StageResult()
    Map<String, Object> context = new HashMap<>()

    if (isLegacyStage) {
      context.put("status", firstPipeline.status)
      if (anyPipelinesFailed) {
        context.put("exception", objectMapper.convertValue(pipelineStatuses.values().first().exception, Map))
      }
    } else {
      result.executionStatuses = pipelineStatuses
    }

    if (pipelineIds.size() == 1 && allPipelinesCompleted) {
      result.insertPipelineContext(firstPipeline.getContext())
    }

    if (allPipelinesSucceeded) {
      return buildTaskResult(ExecutionStatus.SUCCEEDED, context, result)
    }

    if (anyPipelinesFailed) {
      if (allPipelinesCompleted || stageData.monitorBehavior == MonitorPipelineStage.MonitorBehavior.FailFast) {
        stage.appendErrorMessage("At least one monitored pipeline failed, look for errors in failed pipelines")
        return buildTaskResult(ExecutionStatus.TERMINAL, context, result)
      }
    }

    // Finally, if all pipelines completed and we didn't catch that case above it means at least one of those pipelines was CANCELED
    // and we should propagate that result up
    if (allPipelinesCompleted) {
      stage.appendErrorMessage("At least one monitored pipeline was cancelled")
      return buildTaskResult(ExecutionStatus.CANCELED, context, result)
    }

    return buildTaskResult(ExecutionStatus.RUNNING, context, result)
  }

  private buildTaskResult(ExecutionStatus status, Map<String, Object> context, MonitorPipelineStage.StageResult result) {
    return TaskResult.builder(status)
        .context(context)
        .outputs(objectMapper.convertValue(result, Map))
        .build()
  }

  private static effectiveStatus(ExecutionStatus status) {
    if (!status.halt) {
      return status
    }

    if (status == ExecutionStatus.CANCELED) {
      return ExecutionStatus.CANCELED
    }

    return ExecutionStatus.TERMINAL
  }

  private static String buildExceptionMessage(String pipelineName, String message, StageExecution stage) {
    "Exception in child pipeline stage (${pipelineName}: ${stage.name ?: stage.type}): ${message}"
  }
}
