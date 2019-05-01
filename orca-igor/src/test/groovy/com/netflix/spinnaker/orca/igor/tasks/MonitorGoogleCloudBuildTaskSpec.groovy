/*
 * Copyright 2019 Google, Inc.
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
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuild
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MonitorGoogleCloudBuildTaskSpec extends Specification {
  def ACCOUNT = "my-account"
  def BUILD_ID = "0cc67a01-714f-49c7-aaf3-d09b5ec1a18a"

  Execution execution = Mock(Execution)
  IgorService igorService = Mock(IgorService)

  @Subject
  MonitorGoogleCloudBuildTask task = new MonitorGoogleCloudBuildTask(igorService)

  @Unroll
  def "task returns #executionStatus when build returns #buildStatus"() {
    given:
    def igorResponse = GoogleCloudBuild.builder()
      .id(BUILD_ID)
      .status(GoogleCloudBuild.Status.valueOf(buildStatus))
      .build()
    def stage = new Stage(execution, "googleCloudBuild", [
      account: ACCOUNT,
      buildInfo: [
        id: BUILD_ID
      ],
    ])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.getGoogleCloudBuild(ACCOUNT, BUILD_ID) >>igorResponse
    0 * igorService._
    result.getStatus() == executionStatus
    result.getContext().buildInfo == igorResponse

    where:
    buildStatus      | executionStatus
    "STATUS_UNKNOWN" | ExecutionStatus.RUNNING
    "QUEUED"         | ExecutionStatus.RUNNING
    "WORKING"        | ExecutionStatus.RUNNING
    "SUCCESS"        | ExecutionStatus.SUCCEEDED
    "FAILURE"        | ExecutionStatus.TERMINAL
    "INTERNAL_ERROR" | ExecutionStatus.TERMINAL
    "TIMEOUT"        | ExecutionStatus.TERMINAL
    "CANCELLED"      | ExecutionStatus.TERMINAL
  }

  def "task returns RUNNING when communcation with igor fails"() {
    given:
    def stage = new Stage(execution, "googleCloudBuild", [
      account: ACCOUNT,
      buildInfo: [
        id: BUILD_ID
      ],
    ])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.getGoogleCloudBuild(ACCOUNT, BUILD_ID) >> { throw stubRetrofitError() }
    0 * igorService._
    result.getStatus() == ExecutionStatus.RUNNING
  }

  def stubRetrofitError() {
    return Stub(RetrofitError) {
      getKind() >> RetrofitError.Kind.NETWORK
    }
  }
}
