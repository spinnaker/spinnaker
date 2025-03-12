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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuild
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject

class StopGoogleCloudBuildTaskSpec extends Specification {
  def ACCOUNT = "my-account"
  def BUILD = [
    steps: [
      [
        args: [
          "bin/echo",
          "My GCB Stage"
        ],
        name: "debian"
      ]
    ]
  ]
  def BUILD_ID = "98edf783-162c-4047-9721-beca8bd2c275"
  def objectMapper = new ObjectMapper()

  PipelineExecutionImpl execution = Mock(PipelineExecutionImpl)
  IgorService igorService = Mock(IgorService)
  ContextParameterProcessor contextParameterProcessor = Mock(ContextParameterProcessor)

  @Subject
  StopGoogleCloudBuildTask task = new StopGoogleCloudBuildTask(igorService, contextParameterProcessor)

  def "stops a build defined inline"() {
    given:
    def igorResponse = GoogleCloudBuild.builder()
      .id(BUILD_ID)
      .build()
    def stage = new StageExecutionImpl(execution, "googleCloudBuild", [account: ACCOUNT, buildDefinition: BUILD, buildInfo: igorResponse])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.stopGoogleCloudBuild(ACCOUNT, BUILD_ID) >> igorResponse
    result.context.buildInfo == igorResponse
  }
}
