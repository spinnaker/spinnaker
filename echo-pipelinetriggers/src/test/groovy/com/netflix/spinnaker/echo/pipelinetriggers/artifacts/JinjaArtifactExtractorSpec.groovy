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

package com.netflix.spinnaker.echo.pipelinetriggers.artifacts

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.test.RetrofitStubs
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

class JinjaArtifactExtractorSpec extends Specification implements RetrofitStubs {
  def objectMapper = new ObjectMapper()
  def jinjaTemplateService = GroovyMock(JinjaTemplateService)
  def applicationEventPublisher = Mock(ApplicationEventPublisher)

  @Subject
  def artifactExtractor = new JinjaArtifactExtractor(objectMapper, jinjaTemplateService, applicationEventPublisher)

  def "parses an artifact returns it artifacts"() {
    given:
    def properties = [
      group: 'test.group',
      artifact: 'test-artifact',
      version: '1.0',
      messageFormat: 'JAR',
      customFormat: 'false'
    ]
    def hydratedTrigger = enabledJenkinsTrigger.withProperties(properties)
    def inputTrigger = hydratedTrigger

    when:
    def artifacts = artifactExtractor.extractArtifacts(inputTrigger)

    then:
    artifacts.size() == 1
    artifacts[0].name == "test-artifact-1.0"
  }

  def "parseCustomFormat correctly parses booleans and strings"() {
    expect:
    result == artifactExtractor.parseCustomFormat(customFormat)

    where:
    customFormat || result
    true         || true
    false        || false
    "true"       || true
    "false"      || false
  }
}
