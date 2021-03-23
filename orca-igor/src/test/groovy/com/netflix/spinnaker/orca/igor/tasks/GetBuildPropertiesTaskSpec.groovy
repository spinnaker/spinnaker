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
import com.netflix.spinnaker.kork.exceptions.ConfigurationException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class GetBuildPropertiesTaskSpec extends Specification {
  def executionRepository = Mock(ExecutionRepository)
  def artifactUtils = new ArtifactUtils(new ObjectMapper(), executionRepository, new ContextParameterProcessor())
  def buildService = Mock(BuildService)

  def BUILD_NUMBER = 4
  def MASTER = "builds"
  def JOB = "orca"
  def PROPERTY_FILE = "sample.properties"

  @Subject
  GetBuildPropertiesTask task = new GetBuildPropertiesTask(buildService)

  @Shared
  def execution = Stub(PipelineExecutionImpl)

  def "retrieves values from a property file if specified"() {
    given:
    def stage = new StageExecutionImpl(execution, "jenkins", [master: MASTER, job: JOB, buildNumber: 4, propertyFile: PROPERTY_FILE])

    and:
    1 * buildService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER, JOB) >> [val1: "one", val2: "two"]

    when:
    TaskResult result = task.execute(stage)

    then:
    result.context.val1 == 'one'
    result.context.val2 == 'two'
  }

  def "retrieves complex from a property file"() {
    given:
    def stage = new StageExecutionImpl(execution, "jenkins", [master: "builds", job: "orca", buildNumber: 4, propertyFile: PROPERTY_FILE])

    and:
    1 * buildService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER, JOB) >>
      [val1: "one", val2: [complex: true]]

    when:
    TaskResult result = task.execute(stage)

    then:
    result.context.val1 == 'one'
    result.context.val2 == [complex: true]
  }

  def "resolves artifact from a property file"() {
    given:
    def stage = new StageExecutionImpl(execution, "jenkins", [master           : MASTER,
                                                              job              : JOB,
                                                              buildNumber      : BUILD_NUMBER,
                                                              propertyFile     : PROPERTY_FILE,
                                                              expectedArtifacts: [[matchArtifact: [type: "docker/image"]],]])
    def bindTask = new BindProducedArtifactsTask()

    and:
    1 * buildService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER, JOB) >>
        [val1: "one", artifacts: [
          [type: "docker/image",
           reference: "gcr.io/project/my-image@sha256:28f82eba",
           name: "gcr.io/project/my-image",
           version: "sha256:28f82eba"],]]
    bindTask.artifactUtils = artifactUtils
    bindTask.objectMapper = new ObjectMapper()

    when:
    def jenkinsResult = task.execute(stage)
    // We don't have a execution, so we pass context manually
    stage.context << jenkinsResult.context
    def bindResult = bindTask.execute(stage)
    def artifacts = bindResult.outputs["artifacts"] as List<Artifact>

    then:
    bindResult.status == ExecutionStatus.SUCCEEDED
    artifacts.size() == 1
    artifacts[0].name == "gcr.io/project/my-image"
  }

  def "queues the task for re-try after a failed attempt"() {
    given:
    def stage = createStage(PROPERTY_FILE)
    def igorError = Stub(RetrofitError) {
      getResponse() >> new Response("", 500, "", Collections.emptyList(), null)
    }

    and:
    1 * buildService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER, JOB) >>
      { throw igorError }

    when:
    TaskResult result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
  }

  def "fails stage if property file is expected but not returned from jenkins and build passed"() {
    given:
    def stage = createStage(PROPERTY_FILE)

    and:
    1 * buildService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER, JOB) >> [:]

    when:
    task.execute(stage)

    then:
    ConfigurationException e = thrown ConfigurationException
    e.message == "Expected properties file $PROPERTY_FILE but it was either missing, empty or contained invalid syntax"
  }

  def "does not fail stage even if properties are not returned from travis and build passed"() {
    given:
    def stage = createStage(PROPERTY_FILE, "travis-$MASTER")

    and:
    1 * buildService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, "travis-$MASTER", JOB) >> [:]

    when:
    TaskResult result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
  }

  def "does not fetch properties if the property file is empty"() {
    given:
    def stage = createStage("")

    when:
    task.execute(stage)

    then:
    0 * buildService.getPropertyFile(*_)
  }

  def "does not fetch properties if the property file is null"() {
    given:
    def stage = createStage(null)

    when:
    task.execute(stage)

    then:
    0 * buildService.getPropertyFile(*_)
  }

  def createStage(String propertyFile, String ciMaster = MASTER) {
    return new StageExecutionImpl(Stub(PipelineExecutionImpl), "jenkins", [
      master: ciMaster,
      job: JOB,
      buildNumber: BUILD_NUMBER,
      propertyFile: propertyFile
    ])
  }
}
