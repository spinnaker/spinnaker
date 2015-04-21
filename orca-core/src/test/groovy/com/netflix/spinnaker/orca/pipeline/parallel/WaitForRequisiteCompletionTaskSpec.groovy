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

import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.ExecutionStatus.*

class WaitForRequisiteCompletionTaskSpec extends Specification {
  @Subject
  def task = new WaitForRequisiteCompletionTask()

  @Unroll
  def "should SUCCEED iff all requisite stages completed successfully"() {
    given:
    def pipeline = new Pipeline()
    pipeline.stages << new PipelineStage(["refId": "1"])
    pipeline.stages << new PipelineStage(["refId": "2"])

    pipeline.stages[0].status = SUCCEEDED
    pipeline.stages[0].tasks = tasks
    pipeline.stages[1].status = RUNNING
    pipeline.stages[1].tasks = tasks

    when:
    def result = task.execute(new PipelineStage(pipeline, null, [requisiteIds: requisiteIds]))

    then:
    result.status == expectedStatus

    where:
    requisiteIds | tasks                                  || expectedStatus
    []           | [new DefaultTask(status: SUCCEEDED)]   || SUCCEEDED
    ["1"]        | [new DefaultTask(status: SUCCEEDED)]   || SUCCEEDED
    ["1"]        | []                                     || SUCCEEDED
    ["1"]        | [new DefaultTask(status: NOT_STARTED)] || RUNNING
    ["1"]        | [new DefaultTask(status: RUNNING)]     || RUNNING
    ["1", "2"]   | [new DefaultTask(status: SUCCEEDED)]   || RUNNING
    ["2"]        | [new DefaultTask(status: SUCCEEDED)]   || RUNNING
    ["3"]        | [new DefaultTask(status: SUCCEEDED)]   || RUNNING
    ["1", "3"]   | [new DefaultTask(status: SUCCEEDED)]   || RUNNING
  }
}
