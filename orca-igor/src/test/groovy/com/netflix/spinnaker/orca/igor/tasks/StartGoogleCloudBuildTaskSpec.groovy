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
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.igor.model.GoogleCloudBuild
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject

class StartGoogleCloudBuildTaskSpec extends Specification {
  def ACCOUNT = "my-account"
  def BUILD = [
    steps: []
  ]
  def objectMapper = new ObjectMapper()

  Execution execution = Mock(Execution)
  IgorService igorService = Mock(IgorService)
  OortService oortService = Mock(OortService)
  ArtifactResolver artifactResolver = Mock(ArtifactResolver)

  @Subject
  StartGoogleCloudBuildTask task = new StartGoogleCloudBuildTask(igorService, oortService, artifactResolver)

  def "starts a build defined inline"() {
    given:
    def igorResponse = GoogleCloudBuild.builder()
      .id("98edf783-162c-4047-9721-beca8bd2c275")
      .build()
    def stage = new Stage(execution, "googleCloudBuild", [account: ACCOUNT, buildDefinition: BUILD])

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * igorService.createGoogleCloudBuild(ACCOUNT, BUILD) >> igorResponse
    result.context.buildInfo == igorResponse
  }

  def "starts a build defined as an artifact"() {
    given:
    def igorResponse = GoogleCloudBuild.builder()
      .id("810b665a-611d-4ebd-98d1-df7c939843f8")
      .build()
    def artifact = Artifact.builder()
      .uuid("b4541a86-fa45-4a1f-8c55-45cb5cc9d537")
      .artifactAccount("my-account")
      .build()
    def stage = new Stage(execution, "googleCloudBuild", [
      account: ACCOUNT,
      buildDefinitionSource: "artifact",
      buildDefinitionArtifact: [
        artifact: artifact
      ]
    ])

    when:
    TaskResult result = task.execute(stage)

    then:
    artifactResolver.getBoundArtifactForStage(stage, null, artifact) >> artifact
    oortService.fetchArtifact(artifact) >> new Response("", 200, "", Collections.emptyList(), new TypedString(objectMapper.writeValueAsString(BUILD)))

    1 * igorService.createGoogleCloudBuild(ACCOUNT, BUILD) >> igorResponse
    result.context.buildInfo == igorResponse
  }
}
