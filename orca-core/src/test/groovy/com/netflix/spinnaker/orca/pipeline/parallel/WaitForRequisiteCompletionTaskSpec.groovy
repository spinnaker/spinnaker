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

package com.netflix.spinnaker.orca.pipeline.parallel

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForRequisiteCompletionTaskSpec extends Specification {
  @Subject
  def task = new WaitForRequisiteCompletionTask()

  @Unroll
  def "should SUCCEED iff all requisite stages completed successfully"() {
    given:
    def pipeline = new Pipeline()
    pipeline.stages << new PipelineStage(["refId": "1"])
    pipeline.stages << new PipelineStage(["refId": "2"])

    pipeline.stages[0].status = ExecutionStatus.SUCCEEDED
    pipeline.stages[1].status = ExecutionStatus.RUNNING

    when:
    def result = task.execute(new PipelineStage(pipeline, null, [requisiteIds: requisiteIds]))

    then:
    result.status == expectedStatus

    where:
    requisiteIds || expectedStatus
    ["1"]        || ExecutionStatus.SUCCEEDED
    ["1", "2"]   || ExecutionStatus.RUNNING
    ["2"]        || ExecutionStatus.RUNNING
    ["3"]        || ExecutionStatus.RUNNING
    ["1", "3"]   || ExecutionStatus.RUNNING
  }
}
