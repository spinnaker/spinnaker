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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.igor.model.AwsCodeBuildExecution
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

class StopAwsCodeBuildTaskSpec extends Specification {
  def ACCOUNT = "codebuild-account"
  def PROJECT_NAME = "test"
  def ARN = "arn:aws:codebuild:us-west-2:123456789012:build/test:c7715bbf-5c12-44d6-87ef-8149473e02f7"

  PipelineExecutionImpl execution = Mock(PipelineExecutionImpl)
  IgorService igorService = Mock(IgorService)

  @Subject
  StopAwsCodeBuildTask task = new StopAwsCodeBuildTask(igorService)

  def "should stop a build"() {
    given:
    def igorResponse = new AwsCodeBuildExecution(ARN, null, null, null)
    def stage = new StageExecutionImpl(execution, "awsCodeBuild", [
        account: ACCOUNT,
        buildInfo: [
            arn: ARN
        ],
    ])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.stopAwsCodeBuild(ACCOUNT, _) >> igorResponse
    result.status == ExecutionStatus.SUCCEEDED
    result.context.buildInfo.arn == igorResponse.arn
  }

  def "should do nothing if buildInfo doesn't exist"() {
    given:
    def igorResponse = new AwsCodeBuildExecution(ARN, null, null, null)
    def stage = new StageExecutionImpl(execution, "awsCodeBuild", [account: ACCOUNT, projectName: PROJECT_NAME])

    when:
    TaskResult result = task.execute(stage)

    then:
    0 * igorService.stopAwsCodeBuild(ACCOUNT, _)
    result.status == ExecutionStatus.SUCCEEDED
    result.context.buildInfo == null
  }
}
