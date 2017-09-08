/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.expressions

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator.ExpressionEvaluationVersion.*

class PipelineExpressionEvaluatorSpec extends Specification {

  @Unroll
  def "should determine Evaluator version from pipeline map"() {
    expect:
    PipelineExpressionEvaluator.shouldUseV2Evaluator(pipeline) == useV2

    where:
    pipeline                          | useV2
    [:]                               | false
    [spelEvaluator: V1]               | false
    [spelEvaluator: V2]               | true
    [stages: [ [spelEvaluator: V2]]]  | true
  }

  @Unroll
  def "should update Evaluator version if v2 used anywhere"() {
    when:
    PipelineExpressionEvaluator.shouldUseV2Evaluator(pipeline)

    then:
    def stages = pipeline.stages
    (stages != null && stages[0].containsKey("spelEvaluator")) == hasVersion

    where:
    pipeline                                | hasVersion
    [:]                                     | false
    [spelEvaluator: V2, stages: [[:]]]      | true
    [spelEvaluator: V2 ]                    | false
    [spelEvaluator: V1, stages: [[:]]]      | true
  }

  @Unroll
  def "should be able to get version from a stage"() {
    given:
    def stage = new Stage<>(new Pipeline("orca"), "type", stageContext)

    expect:
    PipelineExpressionEvaluator.shouldUseV2Evaluator(stage) == useV2

    where:
    stageContext                                | useV2
    [:]                                         | false
    [spelEvaluator: V2]                         | true
    [spelEvaluator: V2]                         | true
    [spelEvaluator: V1]                         | false
  }

  @Unroll
  def "should be able to override global version per at pipeline level"() {
    given: "global is set to v2"
    PipelineExpressionEvaluator.spelEvaluator = V2

    expect:
    PipelineExpressionEvaluator.shouldUseV2Evaluator(pipeline) == useV2

    where:
    pipeline                                | useV2
    [:]                                     | true
    [spelEvaluator: V1]                     | false
    [spelEvaluator: null]                   | true
    [stages: [ [spelEvaluator: V2]]]        | true
    [stages: [ [spelEvaluator: V1]]]        | false
  }
}
