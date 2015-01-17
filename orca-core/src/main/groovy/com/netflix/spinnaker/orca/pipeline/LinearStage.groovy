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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.batch.StageBuilder
import groovy.transform.Immutable
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobFlowBuilder

/**
 * A base class for +Stage+ implementations that just need to wire a linear sequence of steps.
 */
@CompileStatic
abstract class LinearStage extends StageBuilder {

  private List<InjectedStage> beforeStages = []
  private List<InjectedStage> afterStages = []

  LinearStage(String name) {
    super(name)
  }

  protected abstract List<Step> buildSteps(Stage stage)

  @Override
  JobFlowBuilder build(JobFlowBuilder jobBuilder, Stage stage) {
    def steps = buildSteps(stage)
    def stageIdx = stage.execution.stages.indexOf(stage)
    processBeforeStages(jobBuilder, stageIdx, stage)
    wireSteps(jobBuilder, steps)
    processAfterStages(jobBuilder, stage)
    jobBuilder
  }

  protected void injectBefore(String name, LinearStage stageBuilder, Map<String, Object> context) {
    beforeStages << new InjectedStage(stageBuilder, name, context)
  }

  protected void injectAfter(String name, LinearStage stageBuilder, Map<String, Object> context) {
    afterStages << new InjectedStage(stageBuilder, name, context)
  }

  private void processBeforeStages(JobFlowBuilder jobBuilder, int stageIdx, Stage stage) {
    if (beforeStages) {
      for (beforeStage in beforeStages.reverse()) {
        def newStage = newStage(stage.execution, beforeStage.stageBuilder.type, beforeStage.name, beforeStage.context)
        stage.execution.stages.add(stageIdx, newStage)
        wireSteps(jobBuilder, beforeStage.stageBuilder.buildSteps(newStage))
      }
    }
  }

  private void processAfterStages(JobFlowBuilder jobBuilder, Stage stage) {
    if (afterStages) {
      for (afterStage in afterStages) {
        def newStage = newStage(stage.execution, afterStage.stageBuilder.type, afterStage.name, afterStage.context)
        stage.execution.stages.add(newStage)
        wireSteps(jobBuilder, afterStage.stageBuilder.buildSteps(newStage))
      }
    }
  }

  private JobFlowBuilder wireSteps(JobFlowBuilder jobBuilder, List<Step> steps) {
    (JobFlowBuilder) steps.inject(jobBuilder) { JobFlowBuilder builder, Step step ->
      builder.next(step)
    }
  }

  private static Stage newStage(Execution execution, String type, String name, Map<String, Object> context) {
    if (execution instanceof Orchestration) {
      new OrchestrationStage(execution, type, context)
    } else {
      new PipelineStage((Pipeline)execution, type, name, context)
    }
  }

  @Immutable(knownImmutables = ["stageBuilder"])
  private static class InjectedStage {
    LinearStage stageBuilder
    String name
    Map<String, Object> context
  }
}
