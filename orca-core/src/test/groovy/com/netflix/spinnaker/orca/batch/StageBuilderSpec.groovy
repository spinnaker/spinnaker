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

package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.batch.exceptions.DefaultExceptionHandler
import com.netflix.spinnaker.orca.pipeline.model.*
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.context.ApplicationContext
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class StageBuilderSpec extends Specification {
  @Shared
  def execution = new Pipeline()

  @Shared
  def uuidRegex = '.' * 36 /* super hack */

  @Unroll
  def "should use parent stage id and counter to build child stage id"() {
    when:
    def stage1 = StageBuilder.newStage(execution, null, "NewStage!@#", [:], parent as Stage, null)

    then:
    stage1.id =~ expectedStage1Id

    when:
    def stage2 = StageBuilder.newStage(execution, null, "NewStage!@#", [:], parent as Stage, null)

    then:
    stage2.id =~ expectedStage2Id

    where:
    parent                                              || expectedStage1Id           || expectedStage2Id
    buildParent(execution, "ParentId")                  || "ParentId-1-NewStage"      || "ParentId-2-NewStage"
    buildParent(execution, "GrandParentId", "ParentId") || "GrandParentId-1-NewStage" || "GrandParentId-2-NewStage"
    null                                                || uuidRegex                  || uuidRegex
  }

  def "should handle exceptions when building new stage"() {
    given:
    def stageBuilder = new StageBuilder("bake", [new DefaultExceptionHandler()]) {
      @Override
      protected FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage) {
        throw new IllegalStateException("Expected Exception")
      }
    }
    def stage = new PipelineStage(new Pipeline(), "bake", [:])

    when:
    stageBuilder.build(Mock(FlowBuilder), stage)

    then:
    stage.status == ExecutionStatus.TERMINAL
    stage.startTime != null
    stage.endTime != null
    stage.context.exception.details.errors == ["Expected Exception"]
  }

  @Ignore("coverage moved to StageDefinitonBuilderSpec")
  def "should prepare completed downstream stages for restart"() {
    given:
    def executionRepository = Mock(ExecutionRepository)
    def stageBuilder = new StageBuilder("bake", []) {
      @Override
      protected FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage) {
        throw new IllegalStateException("ignored")
      }
    }
    stageBuilder.applicationContext = Stub(ApplicationContext)

    def pipeline = new Pipeline()
    pipeline.stages = [
        new PipelineStage(pipeline, "1", [:]),
        new PipelineStage(pipeline, "2", [:]),
        new PipelineStage(pipeline, "3", [:])
    ]
    pipeline.stages.eachWithIndex { Stage stage, int index ->
      stage.refId = index.toString()
      if (index > 0) {
        stage.requisiteStageRefIds = ["${index - 1}".toString()]
      }

      ((PipelineStage) stage).tasks = [
        new DefaultTask(startTime: 1, endTime: 2, status: ExecutionStatus.SUCCEEDED)
      ]
    }

    when:
    pipeline.stages[0].status = ExecutionStatus.SUCCEEDED
    pipeline.stages[1].status = ExecutionStatus.SUCCEEDED
    pipeline.stages[2].status = ExecutionStatus.NOT_STARTED
    stageBuilder.prepareStageForRestart(executionRepository, pipeline.stages[0])

    then:
    pipeline.stages[0].context.containsKey("restartDetails")
    pipeline.stages[1].context.containsKey("restartDetails")
    !pipeline.stages[2].context.containsKey("restartDetails")

    // second stage was restarted and should have it's tasks reset
    pipeline.stages[1].tasks[0].startTime == null
    pipeline.stages[1].tasks[0].endTime == null
    pipeline.stages[1].tasks[0].status == ExecutionStatus.NOT_STARTED

    // third stage was not restarted (incomplete status)
    pipeline.stages[2].tasks[0].startTime == 1
    pipeline.stages[2].tasks[0].endTime == 2
    pipeline.stages[2].tasks[0].status == ExecutionStatus.SUCCEEDED
  }

  @Ignore("coverage moved to StageDefinitonBuilderSpec")
  def "should resume a paused pipeline when restarting"() {
    def executionRepository = Mock(ExecutionRepository)
    def stageBuilder = new StageBuilder("bake", []) {
      @Override
      protected FlowBuilder buildInternal(FlowBuilder jobBuilder, Stage stage) {
        throw new IllegalStateException("ignored")
      }
    }
    stageBuilder.applicationContext = Stub(ApplicationContext)

    def pipeline = new Pipeline()
    pipeline.stages = [
      new PipelineStage(pipeline, "1", [:])
    ]

    when:
    pipeline.paused = new Execution.PausedDetails(pauseTime: 100)

    then:
    pipeline.paused.isPaused()

    when:
    stageBuilder.prepareStageForRestart(executionRepository, pipeline.stages[0])

    then:
    1 * executionRepository.resume(pipeline.id, "anonymous", true)
  }

  Stage buildParent(Execution execution, String... parentStageIds) {
    def stages = [] as List<Stage>

    parentStageIds.each {
      def parent = new PipelineStage()
      parent.id = it

      if (stages) {
        parent.parentStageId = stages[-1].id
      }

      stages << parent
    }

    execution.stages.addAll(stages)
    return stages[-1]
  }
}
