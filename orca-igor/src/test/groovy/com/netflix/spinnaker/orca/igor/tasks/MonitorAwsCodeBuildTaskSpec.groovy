/*
 * Copyright 2020 Amazon.com, Inc.
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

package com.netflix.spinnaker.orca.igor.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildExecution
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MonitorAwsCodeBuildTaskSpec extends Specification {
  String ACCOUNT = "my-account"
  String BUILD_ID = "test:c7715bbf-5c12-44d6-87ef-8149473e02f7"
  String ARN = "arn:aws:codebuild:us-west-2:123456789012:build/$BUILD_ID"

  Execution execution = Mock(Execution)
  IgorService igorService = Mock(IgorService)

  @Subject
  MonitorAwsCodeBuildTask task = new MonitorAwsCodeBuildTask(igorService)

  @Unroll
  def "task returns #executionStatus when build returns #buildStatus"() {
    given:
    def igorResponse = new AwsCodeBuildExecution(ARN, buildStatus, null)
    def stage = new Stage(execution, "awsCodeBuild", [
      account: ACCOUNT,
      buildInfo: [
        arn: ARN
      ],
    ])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.getAwsCodeBuildExecution(ACCOUNT, BUILD_ID) >> igorResponse
    0 * igorService._
    result.getStatus() == executionStatus
    result.getContext().buildInfo == igorResponse

    where:
    buildStatus      | executionStatus
    "IN_PROGRESS"    | ExecutionStatus.RUNNING
    "SUCCEEDED"      | ExecutionStatus.SUCCEEDED
    "FAILED"         | ExecutionStatus.TERMINAL
    "FAULT"          | ExecutionStatus.TERMINAL
    "TIMED_OUT"      | ExecutionStatus.TERMINAL
    "STOPPED"        | ExecutionStatus.TERMINAL
    "UNKNOWN"        | ExecutionStatus.TERMINAL
  }

  def "task returns RUNNING when communcation with igor fails"() {
    given:
    def stage = new Stage(execution, "awsCodeBuild", [
      account: ACCOUNT,
      buildInfo: [
        arn: ARN
      ],
    ])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.getAwsCodeBuildExecution(ACCOUNT, BUILD_ID) >> { throw stubRetrofitError() }
    0 * igorService._
    result.getStatus() == ExecutionStatus.RUNNING
  }

  def stubRetrofitError() {
    return Stub(RetrofitError) {
      getKind() >> RetrofitError.Kind.NETWORK
    }
  }
}
