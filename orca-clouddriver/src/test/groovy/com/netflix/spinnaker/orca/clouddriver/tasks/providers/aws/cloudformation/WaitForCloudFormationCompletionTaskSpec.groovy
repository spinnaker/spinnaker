/*
 * Copyright (c) 2019 Schibsted Media Group.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForCloudFormationCompletionTaskSpec extends Specification {

  def oortService = Mock(OortService)

  @Subject
  def waitForCloudFormationCompletionTask = new WaitForCloudFormationCompletionTask(oortService: oortService)

  @Unroll
  def "should succeed if the stack creation is '#status'"() {
    given:
    def pipeline = Execution.newPipeline('orca')
    def context = [
      'credentials': 'creds',
      'cloudProvider': 'aws',
      'kato.tasks': [[resultObjects: [[stackId: 'stackId']]]]
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)
    def stack = [stackStatus: status]

    when:
    def result = waitForCloudFormationCompletionTask.execute(stage)

    then:
    1 * oortService.getCloudFormationStack('stackId') >> stack
    result.status == expectedResult

    where:
    status            | expectedResult
    'CREATE_COMPLETE' | ExecutionStatus.SUCCEEDED
    'UPDATE_COMPLETE' | ExecutionStatus.SUCCEEDED
  }

  @Unroll
  def "should be running while deploy is in '#status'"() {
    given:
    def pipeline = Execution.newPipeline('orca')
    def context = [
      'credentials': 'creds',
      'cloudProvider': 'aws',
      'kato.tasks': [[resultObjects: [[stackId: 'stackId']]]]
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)
    def stack = [stackStatus: 'CREATE_IN_PROGRESS']

    when:
    def result = waitForCloudFormationCompletionTask.execute(stage)

    then:
    1 * oortService.getCloudFormationStack('stackId') >> stack
    result.status == expectedResult

    where:
    status                        | expectedResult
    'CREATE_IN_PROGRESS'          | ExecutionStatus.RUNNING
    'UPDATE_IN_PROGRESS'          | ExecutionStatus.RUNNING
    'ROLLBACK_IN_PROGRESS'        | ExecutionStatus.RUNNING
    'UPDATE_ROLLBACK_IN_PROGRESS' | ExecutionStatus.RUNNING
  }

  def "should be running while clouddriver doesn't return the stack (still not cached)"() {
    def pipeline = Execution.newPipeline('orca')
    def context = [
      'credentials': 'creds',
      'cloudProvider': 'aws',
      'kato.tasks': [[resultObjects: [[stackId: 'stackId']]]]
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)
    def error404 = RetrofitError.httpError("url", new Response("url", 404, "reason", [], null), null, null)

    when:
    def result = waitForCloudFormationCompletionTask.execute(stage)

    then:
    1 * oortService.getCloudFormationStack('stackId') >> { throw error404 }
    result.status == ExecutionStatus.RUNNING
  }

  def "should error on unknown stack status"() {
    given:
    def pipeline = Execution.newPipeline('orca')
    def context = [
      'credentials': 'creds',
      'cloudProvider': 'aws',
      'kato.tasks': [[resultObjects: [[stackId: 'stackId']]]]
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)
    def stack = [stackStatus: 'UNKNOWN']

    when:
    waitForCloudFormationCompletionTask.execute(stage)

    then:
    1 * oortService.getCloudFormationStack('stackId') >> stack
    RuntimeException ex = thrown()
    ex.message.startsWith("Unexpected stack status")
  }

  def "should error when clouddriver responds with an error other than 404"() {
        def pipeline = Execution.newPipeline('orca')
    def context = [
      'credentials': 'creds',
      'cloudProvider': 'aws',
      'kato.tasks': [[resultObjects: [[stackId: 'stackId']]]]
    ]
    def stage = new Stage(pipeline, 'test', 'test', context)
    def error500 = RetrofitError.httpError("url", new Response("url", 500, "reason", [], null), null, null)

    when:
    waitForCloudFormationCompletionTask.execute(stage)

    then:
    1 * oortService.getCloudFormationStack('stackId') >> { throw error500 }
    RuntimeException ex = thrown()
    ex.message == "500 reason"
  }

}
