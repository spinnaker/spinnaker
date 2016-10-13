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

package com.netflix.spinnaker.orca.pipeline

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.pipeline.model.AbstractStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.batch.core.job.flow.Flow
import org.springframework.batch.core.job.flow.support.SimpleFlow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.stereotype.Component

@Component
@Deprecated
@CompileStatic
abstract class ParallelStage extends StageBuilder {
  @Autowired(required = false)
  List<StepProvider> stepProviders = []

  ParallelStage(String name) {
    super(name)
  }

  @Override
  FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage) {
    stage.initializationStage = true

    List<Flow> flows = buildFlows(stage)
    ((AbstractStage) stage).name = parallelStageName(stage, flows.size() > 1)

    if (stage.execution.parallel) {
      def isFirst = {
        if (stage.parentStageId == null) {
          return true
        }
        def allStages = stage.execution.stages
        def siblings = stage.execution.stages.findAll { it.id == stage.parentStageId || (it.parentStageId == stage.parentStageId && it.id != stage.id) }
        return siblings.every { allStages.indexOf(it) > allStages.indexOf(stage) }
      }
      if (isFirst()) {
        jobBuilder = jobBuilder.from(buildStep(stage, "beginParallel", beginParallel()))
      } else {
        jobBuilder.next(buildStep(stage, "beginParallel", beginParallel()))
      }
    } else {
      if (stage.execution.stages.indexOf(stage) == 0) {
        jobBuilder = jobBuilder.start(buildStep(stage, "beginParallel", beginParallel()))
      } else {
        jobBuilder = jobBuilder.next(buildStep(stage, "beginParallel", beginParallel()))
      }
    }

    // revisit when the fix for https://jira.spring.io/browse/BATCH-2346 is available
    def parallelFlowBuilder = new FlowBuilder<Flow>("ParallelStage.${UUID.randomUUID().toString()}")
      .start(new SimpleFlow("NoOp"))
    parallelFlowBuilder = parallelFlowBuilder
      .split(new SimpleAsyncTaskExecutor())
      .add(flows as Flow[])

    jobBuilder = jobBuilder.next(buildStep(stage, "stageStart", StageDetailsTask))

    jobBuilder = jobBuilder
      .next(parallelFlowBuilder.build())
      .next(buildStep(stage, "completeParallel", completeParallel()))

    jobBuilder = jobBuilder.next(buildStep(stage, "stageEnd", StageDetailsTask))

    return jobBuilder
  }

  protected List<Flow> buildFlows(Stage stage) {
    return parallelContexts(stage).collect { Map context ->
      def nextStage = newStage(
        stage.execution, context.type as String, context.name as String, new HashMap(context), stage, SyntheticStageOwner.STAGE_AFTER
      )
      stage.execution.stages.add(nextStage)

      def flowBuilder = new FlowBuilder<Flow>(context.name as String)
      List<Step> steps = buildParallelContextSteps(nextStage)
      steps.eachWithIndex { entry, i ->
        if (i == 0) {
          flowBuilder.from(entry)
        } else {
          flowBuilder.next(entry)
        }
      }

      return flowBuilder.end()
    } ?: [noFlowsFlow(stage)]
  }

  protected Flow noFlowsFlow(Stage stage) {
    new FlowBuilder<Flow>("ParallelStage.noParallelContexts.${UUID.randomUUID().toString()}").from(buildStep(stage, "noopStep", new Task() {
      @Override
      TaskResult execute(Stage s) {
        DefaultTaskResult.SUCCEEDED
      }
    })).end()
  }

  protected Task beginParallel() {
    return new Task() {
      @Override
      TaskResult execute(Stage ignored) {
        return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
      }
    }
  }

  protected List<Step> buildParallelContextSteps(Stage stage) {
    StepProvider provider = stepProviders.find { it.type == stage.context.type }
    return provider.buildSteps(stage)
  }

  abstract String parallelStageName(Stage stage, boolean hasParallelFlows)

  abstract Task completeParallel()

  abstract List<Map<String, Object>> parallelContexts(Stage stage)
}
