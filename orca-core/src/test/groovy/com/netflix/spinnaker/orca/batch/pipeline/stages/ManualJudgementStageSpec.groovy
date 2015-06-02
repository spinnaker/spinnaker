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

package com.netflix.spinnaker.orca.batch.pipeline.stages

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.stages.ManualJudgementStage
import spock.lang.Specification
import spock.lang.Unroll

class ManualJudgementStageSpec extends Specification {
  @Unroll
  void "should return execution status based on judgementStatus"() {
    given:
    def task = new ManualJudgementStage.WaitForManualJudgementTask()

    when:
    def result = task.execute(new PipelineStage(new Pipeline(), "", context))

    then:
    result.status == expectedStatus

    where:
    context                       || expectedStatus
    [:]                           || ExecutionStatus.SUSPENDED
    [judgementStatus: "continue"] || ExecutionStatus.SUCCEEDED
    [judgementStatus: "Continue"] || ExecutionStatus.SUCCEEDED
    [judgementStatus: "stop"]     || ExecutionStatus.TERMINAL
    [judgementStatus: "STOP"]     || ExecutionStatus.TERMINAL
    [judgementStatus: "unknown"]  || ExecutionStatus.SUSPENDED
  }
}
