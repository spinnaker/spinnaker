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
  def "All canary scores should be collected and overall execution status determined with consideration given to configured score thresholds"() {
    given:
    def pipelineBuilder = Pipeline.builder()
    contextCanaryScores.each { pipelineBuilder.withStage("runCanary", "runCanary", [canaryScore: it]) }
    def stage = new Stage<>(pipelineBuilder.build(), "kayentaCanary", "kayentaCanary", [
      canaryConfig: [
        scoreThresholds: scoreThresholds
      ]
    ])

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.context.canaryScores == outputCanaryScores
    taskResult.status == overallExecutionStatus

    where:
    contextCanaryScores | scoreThresholds           || outputCanaryScores | overallExecutionStatus
    [10.5, 40, 60.5]    | [:]                       || [10.5, 40, 60.5]   | ExecutionStatus.SUCCEEDED
    [10.5, 40, 60.5]    | [pass: 60.5]              || [10.5, 40, 60.5]   | ExecutionStatus.SUCCEEDED
    [10.5, 40, 60.5]    | [pass: 55]                || [10.5, 40, 60.5]   | ExecutionStatus.SUCCEEDED
    [10.5, 40, 60.5]    | [marginal: 5, pass: 60.5] || [10.5, 40, 60.5]   | ExecutionStatus.SUCCEEDED
    [10.5, 40, 60.5]    | [marginal: 5, pass: 55]   || [10.5, 40, 60.5]   | ExecutionStatus.SUCCEEDED
    [10.5, 40, 60.5]    | [marginal: 5]             || [10.5, 40, 60.5]   | ExecutionStatus.SUCCEEDED
    [10.5, 40, 60.5]    | [marginal: 5]             || [10.5, 40, 60.5]   | ExecutionStatus.SUCCEEDED
    [65]                | [:]                       || [65]               | ExecutionStatus.SUCCEEDED
    [65]                | [pass: 60.5]              || [65]               | ExecutionStatus.SUCCEEDED
    [65]                | [pass: 55]                || [65]               | ExecutionStatus.SUCCEEDED
    [65]                | [marginal: 5, pass: 60.5] || [65]               | ExecutionStatus.SUCCEEDED
    [65]                | [marginal: 5, pass: 55]   || [65]               | ExecutionStatus.SUCCEEDED
    [65]                | [marginal: 5]             || [65]               | ExecutionStatus.SUCCEEDED
    [65]                | [marginal: 5]             || [65]               | ExecutionStatus.SUCCEEDED
    [10.5, 40, 60.5]    | [pass: 70]                || [10.5, 40, 60.5]   | ExecutionStatus.TERMINAL
    [10.5, 40, 60.5]    | [pass: 70]                || [10.5, 40, 60.5]   | ExecutionStatus.TERMINAL
    [10.5, 40, 60.5]    | [marginal: 5, pass: 70]   || [10.5, 40, 60.5]   | ExecutionStatus.TERMINAL
    [10.5, 40, 60.5]    | [marginal: 5, pass: 70]   || [10.5, 40, 60.5]   | ExecutionStatus.TERMINAL
    [65]                | [:]                       || [65]               | ExecutionStatus.SUCCEEDED
    [65]                | [pass: 70]                || [65]               | ExecutionStatus.TERMINAL
    [65]                | [pass: 70]                || [65]               | ExecutionStatus.TERMINAL
    [65]                | [marginal: 5, pass: 70]   || [65]               | ExecutionStatus.TERMINAL
    [65]                | [marginal: 5, pass: 70]   || [65]               | ExecutionStatus.TERMINAL
    [65]                | [marginal: 68, pass: 70]  || [65]               | ExecutionStatus.TERMINAL
    [65]                | [marginal: 68, pass: 70]  || [65]               | ExecutionStatus.TERMINAL
    [65]                | [marginal: 68]            || [65]               | ExecutionStatus.TERMINAL
    [65]                | [marginal: 68]            || [65]               | ExecutionStatus.TERMINAL
  }
}
