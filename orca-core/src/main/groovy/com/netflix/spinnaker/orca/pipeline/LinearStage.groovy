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

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.pipeline.model.InjectedStageConfiguration
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Stage.SyntheticStageOwner
import com.netflix.spinnaker.orca.pipeline.stages.RestrictExecutionDuringTimeWindow
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.FlowBuilder

/**
 * A base class for +Stage+ implementations that just need to wire a linear sequence of steps.
 */
@CompileStatic
abstract class LinearStage extends StageBuilder implements StepProvider {

  LinearStage(String name) {
    super(name)
  }

  @Override
  FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage) {
    def steps = buildSteps(stage)
    def stageIdx = stage.execution.stages.indexOf(stage)
    /*
     * {@code restrictExecutionDuringTimeWindow} flag tells the builder that this particular {@code Stage}
     * is supposed to run only during certain time windows in a day
     */
    boolean executionRestricted = stage.context.containsKey("restrictExecutionDuringTimeWindow") ?
      stage.context.restrictExecutionDuringTimeWindow as Boolean : false

    def parentStage = stage.execution.stages.find { it.id == stage.parentStageId }
    if (executionRestricted &&
      ((stage.syntheticStageOwner == null && stage.parentStageId == null) || parentStage?.initializationStage)
    ) {
      injectBefore(stage, "restrictExecutionDuringTimeWindow", applicationContext.getBean(RestrictExecutionDuringTimeWindow), stage.context)
    }

    processBeforeStages(jobBuilder, stageIdx, stage)
    wireSteps(jobBuilder, steps)
    processAfterStages(jobBuilder, stage)
    stage.beforeStages.clear()
    stage.afterStages.clear()
    jobBuilder
  }

  protected void injectBefore(Stage stage, String name, StageBuilder stageBuilder, Map<String, Object> context) {
    stage.beforeStages.add(new InjectedStageConfiguration(stageBuilder, name, context))
  }

  protected void injectAfter(Stage stage, String name, StageBuilder stageBuilder, Map<String, Object> context) {
    stage.afterStages.add(new InjectedStageConfiguration(stageBuilder, name, context))
  }

  private void processBeforeStages(FlowBuilder jobBuilder, int stageIdx, Stage stage) {
    if (stage.beforeStages) {
      for (beforeStage in stage.beforeStages.reverse()) {
        def newStage = newStage(stage.execution, beforeStage.stageBuilder.type, beforeStage.name,
          new HashMap(beforeStage.context), stage, SyntheticStageOwner.STAGE_BEFORE)
        stage.execution.stages.add(stageIdx, newStage)
        beforeStage.stageBuilder.build(jobBuilder, newStage)
      }
    }
  }

  private void processAfterStages(FlowBuilder jobBuilder, Stage stage) {
    if (stage.afterStages) {
      for (afterStage in stage.afterStages) {
        def newStage = newStage(stage.execution, afterStage.stageBuilder.type, afterStage.name,
          new HashMap(afterStage.context), stage, SyntheticStageOwner.STAGE_AFTER)
        stage.execution.stages.add(newStage)
        afterStage.stageBuilder.build(jobBuilder, newStage)
      }
    }
  }

  private FlowBuilder wireSteps(FlowBuilder jobBuilder, List<Step> steps) {
    (FlowBuilder) steps.inject(jobBuilder) { FlowBuilder builder, Step step ->
      builder.next(step)
    }
  }
}
