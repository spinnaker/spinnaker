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

package com.netflix.spinnaker.orca.model

import com.netflix.spinnaker.orca.pipeline.Pipeline
import com.netflix.spinnaker.orca.pipeline.Stage
import groovy.transform.Immutable
import org.springframework.batch.core.JobExecution

@Immutable
class PipelineViewModel {
  String id
  String name
  String application
  String status
  Long startTime
  Long endTime
  List<PipelineStageViewModel> stages

  static class PipelineStageViewModel {
    String name
    String status
    Map<String, ? extends Object> context
    Long startTime
    Long endTime
    List<StageStepViewModel> steps
  }

  static class StageStepViewModel {
    String name
    String status
    Long startTime
    Long endTime
  }

  static final String PENDING_STAGE_STATUS_VAL = "NOT_STARTED"

  static PipelineViewModel fromPipelineAndExecution(Pipeline pipeline, JobExecution jobExecution) {
    Map<String, PipelineStageViewModel> stages = [:]

    for (stepExecution in jobExecution.stepExecutions) {
      def stage = "init"
      def step = stepExecution.stepName
      if (stepExecution.stepName.contains('.')) {
        def parts = stepExecution.stepName.tokenize('.')
        stage = parts[0]
        step = parts[1]
      }
      if (!stages.containsKey(stage)) {
        stages[stage] = new PipelineStageViewModel(name: stage, context: [:], steps: [])
      }
      stages[stage].steps << new StageStepViewModel(name: step, status: stepExecution.exitStatus.exitCode,
        startTime: stepExecution.startTime?.time, endTime: stepExecution.endTime?.time)
    }
    for (stage in pipeline.stages) {
      if (!stages.containsKey(stage.type)) {
        stages[stage.type] = new PipelineStageViewModel(name: stage.type, context: stage.context, steps: [])
      }
    }
    for (stage in stages.values()) {
      if (jobExecution.executionContext.get(stage.name) instanceof Stage) {
        stage.context = ((Stage)jobExecution.executionContext.get(stage.name)).context
      }
      stage.startTime = stage.steps?.getAt(0)?.startTime
      stage.status = PENDING_STAGE_STATUS_VAL
      if (stage.steps.size() > 0) {
        def lastStep = stage.steps.getAt(-1)
        stage.status = lastStep?.status
        stage.endTime = lastStep?.endTime
      }
    }
    def pipelineStages = stages.values() as List
    def lastExecutedStage = pipelineStages.reverse().find { it.status != PENDING_STAGE_STATUS_VAL }
    def status = lastExecutedStage?.status ?: "EXECUTING"
    def startTime = pipelineStages ? pipelineStages?.getAt(0)?.startTime : null
    def endTime = lastExecutedStage?.endTime ?: null
    new PipelineViewModel(id: pipeline.id, name: pipeline.name, application: pipeline.application,
      stages: pipelineStages, status: status, startTime: startTime, endTime: endTime)
  }
}
