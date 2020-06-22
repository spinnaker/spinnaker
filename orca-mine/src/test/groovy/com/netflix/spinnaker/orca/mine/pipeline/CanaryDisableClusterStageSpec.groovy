/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.orca.api.pipeline.CancellableStage
import com.netflix.spinnaker.orca.kayenta.pipeline.CanaryDisableClusterStage
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class CanaryDisableClusterStageSpec extends Specification {

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
    def destroyCanaryClusterStage = new CanaryDisableClusterStage(canaryStage: canaryStageBuilder)
    def stage = new StageExecutionImpl(pipeline, "pipelineStage", [
        canary: [id: "canaryId"]
    ])
    stage.setRequisiteStageRefIds(["1"])

    when:
    stage.execution.canceled = true
    def result = destroyCanaryClusterStage.cancel(stage)

    then:
    result.details == [:]
    1 * canaryStageBuilder.cancel(canaryStage) >> {
      new CancellableStage.Result(stage, [:])
    }
  }

  def "should raise exception if no upstream canary stage found"() {
    def destroyClusterStage = new CanaryDisableClusterStage()
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "pipelineStage", [
        canary: [id: "canaryId"]
    ])

    when:
    stage.execution.canceled = true
    destroyClusterStage.cancel(stage)

    then:
    thrown(IllegalStateException)
  }
}
