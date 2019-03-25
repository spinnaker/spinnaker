/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.igor.artifacts

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import spock.lang.Specification
import spock.lang.Subject

import static org.assertj.core.api.Assertions.assertThat

class ArtifactServiceSpec extends Specification {

  @Subject
  ArtifactServices artifactServices = new ArtifactServices()
  
  void setup() {
    Map<String, ArtifactService> services = new HashMap<>()
    services.put("artifactory", new TestArtifactService())
    artifactServices.addServices(services)
  }
  
  def "finds matching service"() {
    when:
    def service = artifactServices.getService("artifactory")

    then:
    assertThat(service).isNotNull()
  }

  def "does not find a non-matching service"() {
    when:
    def service = artifactServices.getService("what")

    then:
    assertThat(service).isNull()
  }

  def "service finds artifact versions"() {
    when:
    def service = artifactServices.getService("artifactory")
    def versions = service.getArtifactVersions("test")

    then:
    assertThat(versions).isNotNull()
    assertThat(versions).isNotEmpty()
    versions.size() > 0
  }

  def "service finds artifact"() {
    when:
    def service = artifactServices.getService("artifactory")
    def artifact = service.getArtifact("test", "v0.4.0")

    then:
    assertThat(artifact).isNotNull()
    artifact.name.equals("test")
    artifact.version.equals("v0.4.0")
  }

  def "versions list is empty when no versions found"() {
    when:
    def service = artifactServices.getService("artifactory")
    def versions = service.getArtifactVersions("blah")

    then:
    assertThat(versions).isNotNull()
    assertThat(versions).isEmpty()
  }

  def "404 is thrown when artifact not found"() {
    when:
    def service = artifactServices.getService("artifactory")
    service.getArtifact("blah","v0.0.1")

    then:
    thrown(NotFoundException)
  }
  
}
