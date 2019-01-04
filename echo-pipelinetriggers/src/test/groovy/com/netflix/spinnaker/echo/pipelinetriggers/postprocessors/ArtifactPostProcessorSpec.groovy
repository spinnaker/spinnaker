/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.artifacts.JinjaTemplateService
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import spock.lang.Specification
import spock.lang.Subject

class ArtifactPostProcessorSpec extends Specification implements RetrofitStubs {
  def objectMapper = new ObjectMapper()
  def jinjaTemplateService = GroovyMock(JinjaTemplateService)
  def existingArtifact = Artifact.builder()
    .type("testType").name("testName").reference("testReference").build()

  @Subject
  def artifactPostProcessor = new ArtifactPostProcessor(objectMapper, jinjaTemplateService)

  def "does not modify a pipeline without a template in the property file"() {
    given:
    def inputPipeline = createPipelineWith(enabledJenkinsTrigger)

    when:
    def outputPipeline = artifactPostProcessor.processPipeline(inputPipeline)

    then:
    outputPipeline.id == inputPipeline.id
  }

  def "leaves existing artifacts in place"() {
    given:
    def inputPipeline = createPipelineWith(enabledJenkinsTrigger).withReceivedArtifacts([existingArtifact])

    when:
    def outputPipeline = artifactPostProcessor.processPipeline(inputPipeline)

    then:
    outputPipeline.receivedArtifacts.size() == 1
    outputPipeline.receivedArtifacts[0].name == "testName"
  }

  def "parses an artifact and appends it to the received artifacts"() {
    given:
    def properties = [
      group: 'test.group',
      artifact: 'test-artifact',
      version: '1.0',
      messageFormat: 'JAR',
      customFormat: 'false'
    ]
    def hydratedTrigger = enabledJenkinsTrigger.withProperties(properties)
    def inputPipeline = createPipelineWith(enabledJenkinsTrigger)
      .withTrigger(hydratedTrigger).withReceivedArtifacts([existingArtifact])

    when:
    def outputPipeline = artifactPostProcessor.processPipeline(inputPipeline)

    then:
    outputPipeline.receivedArtifacts.size() == 2
    outputPipeline.receivedArtifacts[0].name == "testName"
    outputPipeline.receivedArtifacts[1].name == "test-artifact-1.0"
  }

  def "parseCustomFormat correctly parses booleans and strings"() {
    expect:
    result == artifactPostProcessor.parseCustomFormat(customFormat)

    where:
    customFormat || result
    true         || true
    false        || false
    "true"       || true
    "false"      || false
  }
}
