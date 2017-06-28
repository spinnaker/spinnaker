/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import spock.lang.Specification
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionEngine.v2

class StageDefinitionBuilderSpec extends Specification {

  def executionRepository = Mock(ExecutionRepository)

  def "should prepare completed downstream stages for restart"() {
    given:
    def stageBuilder = new TestStage()

    def pipeline = new Pipeline(executionEngine: v2)
    pipeline.stages = [
      new Stage<>(pipeline, "1"),
      new Stage<>(pipeline, "2"),
      new Stage<>(pipeline, "3"),
      new Stage<>(pipeline, "4")
    ]
    pipeline.stages.eachWithIndex { Stage<Pipeline> stage, int index ->
      stage.refId = index.toString()
      if (index > 0) {
        stage.requisiteStageRefIds = ["${index - 1}".toString()]
      } else {
        stage.requisiteStageRefIds = []
      }

      stage.tasks = [
        new Task(startTime: 1L, endTime: 2L, status: SUCCEEDED)
      ]
    }

    and:
    pipeline.stages[0].status = SUCCEEDED
    pipeline.stages[1].status = SUCCEEDED
    pipeline.stages[2].status = NOT_STARTED

    when:
    stageBuilder.prepareStageForRestart(executionRepository, pipeline.stages[0], [stageBuilder])

    then:
    pipeline.stages[0].context.containsKey("restartDetails")
    pipeline.stages[1].context.containsKey("restartDetails")
    !pipeline.stages[2].context.containsKey("restartDetails")

    and: "second stage was restarted and should have its tasks reset"
    pipeline.stages[1].tasks[0].startTime == null
    pipeline.stages[1].tasks[0].endTime == null
    pipeline.stages[1].tasks[0].status == NOT_STARTED

    and: "third stage was not restarted (incomplete status)"
    pipeline.stages[2].tasks[0].startTime == 1L
    pipeline.stages[2].tasks[0].endTime == 2L
    pipeline.stages[2].tasks[0].status == SUCCEEDED

    and: "fourth stage was restarted"
    pipeline.stages[1].tasks[0].startTime == null
    pipeline.stages[1].tasks[0].endTime == null
    pipeline.stages[1].tasks[0].status == NOT_STARTED
  }

  def "should resume a paused pipeline when restarting"() {
    given:
    def stageBuilder = new TestStage()

    def pipeline = new Pipeline(executionEngine: v2)
    pipeline.stages = [
      new Stage<>(pipeline, "1"),
    ]

    and:
    pipeline.paused = new Execution.PausedDetails(pauseTime: 100)

    expect:
    pipeline.paused.isPaused()

    when:
    stageBuilder.prepareStageForRestart(executionRepository, pipeline.stages[0], [stageBuilder])

    then:
    1 * executionRepository.resume(pipeline.id, _, true)
  }

  @CompileStatic
  class TestStage implements StageDefinitionBuilder {

  }
}
