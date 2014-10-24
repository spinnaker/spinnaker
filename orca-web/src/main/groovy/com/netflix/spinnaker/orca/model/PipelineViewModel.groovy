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

  static PipelineViewModel fromPipelineAndExecution(Pipeline pipeline, JobExecution jobExecution) {
    Map<String, List<StageStepViewModel>> steps = [:]
    for (stepExecution in jobExecution.stepExecutions) {
      def stage = "pipeline"
      def step = stepExecution.stepName
      if (stepExecution.stepName.contains('.')) {
        def parts = stepExecution.stepName.tokenize('.')
        stage = parts[0]
        step = parts[1]
      }
      if (!steps.containsKey(stage)) {
        steps[stage] = []
      }
      steps[stage] << new StageStepViewModel(name: step, status: stepExecution.exitStatus.exitCode,
        startTime: stepExecution.startTime?.time, endTime: stepExecution.endTime?.time)
    }
    List<PipelineStageViewModel> stages = []
    for (entry in steps) {
      def stageName = entry.key
      def stageSteps = entry.value
      def context = [:]
      if (jobExecution.executionContext.get(stageName) instanceof Stage) {
        context = ((Stage)jobExecution.executionContext.get(stageName)).context
      }
      stages << new PipelineStageViewModel(name: stageName, status: stageSteps[-1].status, context: context,
        steps: stageSteps, startTime: stageSteps[0]?.startTime, endTime: stageSteps[-1]?.endTime)
    }
    new PipelineViewModel(id: pipeline.id, name: pipeline.name, application: pipeline.application, stages: stages,
      status: stages[-1]?.status)
  }
}
