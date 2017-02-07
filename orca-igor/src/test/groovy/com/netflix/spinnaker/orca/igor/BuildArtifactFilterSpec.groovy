/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.orca.igor

import org.springframework.mock.env.MockEnvironment
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class BuildArtifactFilterSpec extends Specification {
  def environment = new MockEnvironment()

  @Subject
  def buildArtifactFilter = new BuildArtifactFilter(environment: environment)

  @Unroll
  def 'should filter artifacts based on environment configuration'() {
    given:
    environment.withProperty(BuildArtifactFilter.MAX_ARTIFACTS_PROP, maxArtifacts.toString())

    if (preferredArtifacts) {
      environment.withProperty(BuildArtifactFilter.PREFERRED_ARTIFACTS_PROP, preferredArtifacts)
    }

    expect:
    buildArtifactFilter.filterArtifacts(artifacts)*.fileName == expectedArtifacts

    where:
    maxArtifacts | preferredArtifacts | expectedArtifacts
    1            | 'deb'              | ['foo1.deb']
    2            | 'deb'              | ['foo1.deb', 'foo2.rpm']
    2            | 'deb,properties'   | ['foo1.deb', 'foo3.properties']
    2            | 'properties,rpm'   | ['foo3.properties', 'foo2.rpm']
    11           | null               | ['foo1.deb', 'foo2.rpm', 'foo3.properties', 'foo4.yml', 'foo5.json', 'foo6.xml', 'foo7.txt', 'foo8.nupkg']
    1            | 'nupkg'            | ['foo8.nupkg']

    artifacts = [
      [fileName: 'foo1.deb'],
      [fileName: 'foo2.rpm'],
      [fileName: 'foo3.properties'],
      [fileName: 'foo4.yml'],
      [fileName: 'foo5.json'],
      [fileName: 'foo6.xml'],
      [fileName: 'foo7.txt'],
      [fileName: 'foo8.nupkg'],
    ]
  }
}
