/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class MonitorCanaryStageSpec extends Specification {
  def mineService = Mock(MineService)

  def "should short-circuit if canary registered but execution not explicitly canceled"() {
    given:
    def monitorCanaryStage = new MonitorCanaryStage(mineService: mineService)
    def stage = new Stage(Execution.newPipeline("orca"), "pipelineStage", [
      canary: [id: "canaryId"]
    ])

    when:
    stage.execution.canceled = false
    def result = monitorCanaryStage.cancel(stage)

    then:
    result == null
    0 * mineService.cancelCanary(_, _)
  }

  def "should propagate cancel upstream if canary registered and execution explicitly canceled"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        type = CanaryStage.PIPELINE_CONFIG_TYPE
      }
    }
    def canaryStage = pipeline.stageByRef("1")
    def canaryStageBuilder = Mock(CanaryStage)
    def monitorCanaryStage = new MonitorCanaryStage(mineService: mineService, canaryStage: canaryStageBuilder)
    def stage = new Stage(pipeline, "pipelineStage", [
      canary: [id: "canaryId"]
    ])
    stage.setRequisiteStageRefIds(["1"])

    when:
    stage.execution.canceled = true
    def result = monitorCanaryStage.cancel(stage)

    then:
    result.details.canary == [canceled: true]
    1 * canaryStageBuilder.cancel(canaryStage) >> {
      new CancellableStage.Result(stage, [:])
    }
    1 * mineService.cancelCanary("canaryId", _) >> { return [canceled: true] }
  }

  def "should raise exception if no upstream canary stage found"() {
    def monitorCanaryStage = new MonitorCanaryStage(mineService: mineService)
    def stage = new Stage(Execution.newPipeline("orca"), "pipelineStage", [
      canary: [id: "canaryId"]
    ])

    when:
    stage.execution.canceled = true
    monitorCanaryStage.cancel(stage)

    then:
    thrown(IllegalStateException)
  }
}
