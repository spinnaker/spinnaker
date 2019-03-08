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

package com.netflix.spinnaker.igor.artifacts

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.kork.artifacts.parsing.DefaultJinjavaFactory
import com.netflix.spinnaker.kork.artifacts.parsing.JinjaArtifactExtractor
import spock.lang.Specification
import spock.lang.Subject

class ArtifactExtractorSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def jinjaTemplateService = GroovyMock(JinjaTemplateService)
  def jinjaArtifactExtractorFactory = new JinjaArtifactExtractor.Factory(new DefaultJinjavaFactory())

  @Subject
  def artifactExtractor = new ArtifactExtractor(jinjaTemplateService, jinjaArtifactExtractorFactory)

  def "parses an artifact returns it artifacts"() {
    given:
    def properties = [
      group: 'test.group',
      artifact: 'test-artifact',
      version: '1.0',
      messageFormat: 'JAR',
      customFormat: 'false'
    ]
    def build = new GenericBuild()
    build.properties = properties

    when:
    def artifacts = artifactExtractor.extractArtifacts(build)

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
