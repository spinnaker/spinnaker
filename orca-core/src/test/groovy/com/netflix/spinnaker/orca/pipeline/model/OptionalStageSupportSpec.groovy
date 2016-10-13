/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.orca.pipeline.model

import com.netflix.spinnaker.orca.ExecutionStatus
import spock.lang.Specification
import spock.lang.Unroll

class OptionalStageSupportSpec extends Specification {
  @Unroll
  def "should support expression-based optionality"() {
    given:
    def pipeline = new Pipeline()
    pipeline.trigger.parameters = [
      "p1": "v1"
    ]
    pipeline.stages << new PipelineStage(pipeline, "", "Test1", [:])
    pipeline.stages[0].status = ExecutionStatus.FAILED_CONTINUE

    pipeline.stages << new PipelineStage(pipeline, "", "Test2", [:])
    pipeline.stages[1].status = ExecutionStatus.SUCCEEDED

    and:
    def stage = new PipelineStage(pipeline, "", [
      stageEnabled: optionalConfig
    ])

    expect:
    OptionalStageSupport.isOptional(stage) == expectedOptionality

    where:
    optionalConfig                                                                                                   || expectedOptionality
    [:]                                                                                                              || false
    [type: "expression"]                                                                                             || false
    [type: "expression", expression: ""]                                                                             || false
    [type: "expression", expression: null]                                                                           || false
    [type: "expression", expression: "parameters.p1 == 'v1'"]                                                        || false
    [type: "expression", expression: "parameters.p1 == 'v2'"]                                                        || true
    [type: "expression", expression: "execution.stages.?[name == 'Test2'][0]['status'].name() == 'FAILED_CONTINUE'"] || true
    [type: "expression", expression: "execution.stages.?[name == 'Test1'][0]['status'].name() == 'FAILED_CONTINUE'"] || false
    [type: "expression", expression: "true"]                                                                         || false
    [type: "expression", expression: "false"]                                                                        || true
  }

  @Unroll
  def "should check optionality of parent stages"() {
    given:
    def pipeline = new Pipeline()
    pipeline.trigger.parameters = [
      "p1": "v1"
    ]
    pipeline.stages << new PipelineStage(pipeline, "", "Test1", [
      stageEnabled: optionalConfig
    ])
    pipeline.stages[0].status = ExecutionStatus.FAILED_CONTINUE

    pipeline.stages << new PipelineStage(pipeline, "", "Test2", [:])
    pipeline.stages[1].status = ExecutionStatus.SUCCEEDED
    pipeline.stages[1].syntheticStageOwner = SyntheticStageOwner.STAGE_AFTER
    pipeline.stages[1].parentStageId = pipeline.stages[0].id

    expect:
    OptionalStageSupport.isOptional(pipeline.stages[1]) == expectedOptionality

    where:
    optionalConfig                                            || expectedOptionality
    [type: "expression", expression: "parameters.p1 == 'v1'"] || false
    [type: "expression", expression: "parameters.p1 == 'v2'"] || true
  }
}
