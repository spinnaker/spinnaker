/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.kayenta.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class AggregateCanaryResultsTaskSpec extends Specification {

  @Shared
  def task = new AggregateCanaryResultsTask()

  @Unroll
  def "All canary scores should be collected and aggregated as requested"() {
    given:
    def pipelineBuilder = Pipeline.builder()
    contextCanaryScores.each { pipelineBuilder.withStage("runCanary", "runCanary", [canaryScore: it]) }
    def stage = new Stage<>(pipelineBuilder.build(), "kayentaCanary", "kayentaCanary", [
      canaryConfig: [
        combinedCanaryResultStrategy: resultStrategy
      ]
    ])

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == ExecutionStatus.SUCCEEDED
    taskResult.stageOutputs.canaryScores == outputCanaryScores
    taskResult.stageOutputs.overallScore == outputOverallScore

    where:
    contextCanaryScores | resultStrategy || outputCanaryScores | outputOverallScore
    [10.5, 40, 60.5]    | "AVERAGE"      || [10.5, 40, 60.5]   | 37.0
    [10.5, 40, 60.5]    | "LOWEST"       || [10.5, 40, 60.5]   | 10.5
    [10.5, 40, 60.5]    | ""             || [10.5, 40, 60.5]   | 10.5
    [10.5, 40, 60.5]    | null           || [10.5, 40, 60.5]   | 10.5
    [78]                | "AVERAGE"      || [78]               | 78.0
    [78]                | "LOWEST"       || [78]               | 78.0
    [78]                | ""             || [78]               | 78.0
    [78]                | null           || [78]               | 78.0
    []                  | null           || []                 | -99.0
    null                | null           || []                 | -99.0
  }
}
