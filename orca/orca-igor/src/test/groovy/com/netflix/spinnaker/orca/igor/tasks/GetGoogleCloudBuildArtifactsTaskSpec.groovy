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

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import okhttp3.Request
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject

class GetGoogleCloudBuildArtifactsTaskSpec extends Specification {
  def ACCOUNT = "my-account"
  def BUILD_ID = "f2526c98-0c20-48ff-9f1f-736503937084"

  PipelineExecutionImpl execution = Mock(PipelineExecutionImpl)
  IgorService igorService = Mock(IgorService)

  @Subject
  GetGoogleCloudBuildArtifactsTask task = new GetGoogleCloudBuildArtifactsTask(igorService)

  def "fetches artifacts from igor and returns success"() {
    given:
    def artifacts = [
      Artifact.builder().reference("abc").build(),
      Artifact.builder().reference("def").build()
    ]
    def stage = new StageExecutionImpl(execution, "googleCloudBuild", [
      account: ACCOUNT,
      buildInfo: [
        id: BUILD_ID
      ],
    ])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.getGoogleCloudBuildArtifacts(ACCOUNT, BUILD_ID) >> Calls.response(artifacts)
    0 * igorService._
    result.getStatus() == ExecutionStatus.SUCCEEDED
    result.getOutputs().get("artifacts") == artifacts
  }

  def "task returns RUNNING when communcation with igor fails"() {
    given:
    def stage = new StageExecutionImpl(execution, "googleCloudBuild", [
      account: ACCOUNT,
      buildInfo: [
        id: BUILD_ID
      ],
    ])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.getGoogleCloudBuildArtifacts(ACCOUNT, BUILD_ID) >> { throw stubNetworkError() }
    0 * igorService._
    result.getStatus() == ExecutionStatus.RUNNING
  }

  def stubNetworkError() {
    return new SpinnakerNetworkException(new IOException("timeout"), new Request.Builder().url("http://some-url").build())
  }
}
