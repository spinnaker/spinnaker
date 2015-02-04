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

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.InjectedStageConfiguration
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Stage.SyntheticStageOwner
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.ExecutionStatus.SUSPENDED
/**
 * A base class for +Stage+ implementations that just need to wire a linear sequence of steps.
 */
@CompileStatic
abstract class LinearStage extends StageBuilder {

  LinearStage(String name) {
    super(name)
  }

  protected abstract List<Step> buildSteps(Stage stage)

  @Override
  JobFlowBuilder build(JobFlowBuilder jobBuilder, Stage stage) {
    def steps = buildSteps(stage)
    def stageIdx = stage.execution.stages.indexOf(stage)
    /*
     * {@code restrictExecutionInTimeWindow} flag tells the builder that this particular {@code Stage}
     * is not supposed to be run during a particular timed window in a day
     */
    if (stage.execution instanceof Pipeline &&
        stage.context.containsKey("restrictExecutionDuringTimeWindow") && stage.context.restrictExecutionDuringTimeWindow as Boolean) {
      injectBefore(stage, "restrictExecutionDuringTimeWindow", applicationContext.getBean(RestrictExecutionDuringTimeWindow), stage.context)
    }
    processBeforeStages(jobBuilder, stageIdx, stage)
    wireSteps(jobBuilder, steps)
    processAfterStages(jobBuilder, stage)
    stage.beforeStages.clear()
    stage.afterStages.clear()
    jobBuilder
  }

  protected void injectBefore(Stage stage, String name, LinearStage stageBuilder, Map<String, Object> context) {
    stage.beforeStages.add(new InjectedStageConfiguration(stageBuilder, name, context))
  }

  protected void injectAfter(Stage stage, String name, LinearStage stageBuilder, Map<String, Object> context) {
    stage.afterStages.add(new InjectedStageConfiguration(stageBuilder, name, context))
  }

  private void processBeforeStages(JobFlowBuilder jobBuilder, int stageIdx, Stage stage) {
    if (stage.beforeStages) {
      for (beforeStage in stage.beforeStages.reverse()) {
        def newStage = newStage(stage.execution, beforeStage.stageBuilder.type, beforeStage.name,
          new HashMap(beforeStage.context), stage, SyntheticStageOwner.STAGE_BEFORE)
        stage.execution.stages.add(stageIdx, newStage)
        wireSteps(jobBuilder, beforeStage.stageBuilder.buildSteps(newStage))
      }
    }
  }

  private void processAfterStages(JobFlowBuilder jobBuilder, Stage stage) {
    if (stage.afterStages) {
      for (afterStage in stage.afterStages) {
        def newStage = newStage(stage.execution, afterStage.stageBuilder.type, afterStage.name,
          new HashMap(afterStage.context), stage, SyntheticStageOwner.STAGE_AFTER)
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

  private static Stage newStage(Execution execution, String type, String name, Map<String, Object> context,
                                Stage parent, SyntheticStageOwner stageOwner) {
    def stage
    if (execution instanceof Orchestration) {
      stage = new OrchestrationStage(execution, type, context)
    } else {
      stage = new PipelineStage((Pipeline)execution, type, name, context)
    }
    stage.parentStageId = parent.id
    stage.syntheticStageOwner = stageOwner
    stage
  }

  /**
   * A stage that suspends execution of pipeline if the current stage is restricted to run during a time window and
   * current time is within that window.
   */
  @Component
  private static class RestrictExecutionDuringTimeWindow extends LinearStage {
    private static final String MAYO_CONFIG_NAME = "restrictExecutionDuringTimeWindow"

    RestrictExecutionDuringTimeWindow() {
      super(MAYO_CONFIG_NAME)
    }

    @Override
    protected List<Step> buildSteps(Stage stage) {
      [buildStep(stage, "suspendExecutionDuringTimeWindow", SuspendExecutionDuringTimeWindowTask)]
    }
  }

  @Component
  private static class SuspendExecutionDuringTimeWindowTask implements com.netflix.spinnaker.orca.Task {
    @Override
    TaskResult execute(Stage stage) {
      def now = new Date()
      def scheduled = getTimeInWindow(stage, new Date())
      if (now.compareTo(scheduled) in [0, 1]) {
        new DefaultTaskResult(SUCCEEDED)
      } else {
        if (stage.execution instanceof Pipeline) {
          Pipeline pipeline = (Pipeline) stage.execution
          pipeline.scheduledDate = scheduled
          new DefaultTaskResult(SUSPENDED)
        } else {
          new DefaultTaskResult(SUCCEEDED)
        }
      }
    }

    /**
     * Calculates the date-time which is outside of the blackout window. Also, considers the business hours for window calculation
     * if passed in the stage context.
     * @param stage
     * @param currentDateTime
     * @return
     */
    @VisibleForTesting
    private static Date getTimeInWindow(Stage stage, Date currentDateTime) {  // Passing in the current date to allow unit testing
      def restrictedExecutionWindow = stage.context.restrictedExecutionWindow as Map

      int startHour = restrictedExecutionWindow.startHour as Integer
      int startMin = restrictedExecutionWindow.startMin as Integer
      int endHour = restrictedExecutionWindow.endHour as Integer
      int endMin = restrictedExecutionWindow.endMin as Integer

      // Sensible assumptions in PST
      int dayStartHour = restrictedExecutionWindow.containsKey("dayStartHour") ? stage.context.dayStartHour as Integer : 7
      int dayStartMin = restrictedExecutionWindow.containsKey("dayStartMin") ? stage.context.dayStartMin as Integer : 0
      int dayEndHour = restrictedExecutionWindow.containsKey("dayEndHour") ? stage.context.dayEndHour as Integer : 18
      int dayEndMin = restrictedExecutionWindow.containsKey("dayStartMin") ? stage.context.dayStartMin as Integer : 0

      def now = new Date()
      def dayStart = getUpdatedDate(now, dayStartHour, dayStartMin, 0)
      def dayEnd = getUpdatedDate(now, dayEndHour, dayEndMin, 0)
      def start = getUpdatedDate(now, startHour, startMin, 0)
      def end = getUpdatedDate(now, endHour, endMin, 0)

      if (currentDateTime.before(dayStart)) {
        currentDateTime[Calendar.HOUR_OF_DAY] = dayStart[Calendar.HOUR_OF_DAY]
        currentDateTime[Calendar.MINUTE] = dayStart[Calendar.MINUTE]
      }

      if (currentDateTime.after(start)) {
        currentDateTime[Calendar.HOUR_OF_DAY] = end[Calendar.HOUR_OF_DAY]
        currentDateTime[Calendar.MINUTE] = end[Calendar.MINUTE]
      }

      if (currentDateTime.after(dayEnd)) {
        currentDateTime[Calendar.HOUR_OF_DAY] = dayStart[Calendar.HOUR_OF_DAY]
        currentDateTime[Calendar.MINUTE] = dayStart[Calendar.MINUTE]
        currentDateTime = currentDateTime + 1
      }

      return currentDateTime
    }

    private static Date getUpdatedDate(Date date, int hour, int min, int seconds) {
      Calendar calendar = Calendar.instance
      calendar.setTime(date)
      calendar.set(Calendar.HOUR_OF_DAY, hour)
      calendar.set(Calendar.MINUTE, min)
      calendar.set(Calendar.SECOND, seconds)
      return calendar.time
    }
  }

}
