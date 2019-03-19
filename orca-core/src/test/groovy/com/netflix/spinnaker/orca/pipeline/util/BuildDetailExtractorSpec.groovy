/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.pipeline.util


import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BuildDetailExtractorSpec extends Specification {

  @Shared
  BuildDetailExtractor buildDetailExtractor = new BuildDetailExtractor()

  @Unroll
  def "Default detail from buildInfo"() {

    when:
    buildDetailExtractor.tryToExtractJenkinsBuildDetails(buildInfo, result)

    then:
    result == expectedResult

    where:
    buildInfo                                                                                                                                              | result | expectedResult
    ["name": "SPINNAKER", "number": "9001", "url": "http://spinnaker.jenkis.test.netflix.net/job/SPINNAKER-package-echo/69/"]                              | [:]    | ["job": "SPINNAKER", "buildNumber": 9001, "buildInfoUrl": "http://spinnaker.jenkis.test.netflix.net/job/SPINNAKER-package-echo/69/", "buildHost": "http://spinnaker.jenkis.test.netflix.net/"]
    ["name": "organization/SPINNAKER", "number": "9001", "url": "http://spinnaker.travis.test.netflix.net/organization/SPINNAKER-package-echo/builds/69/"] | [:]    | ["job": "organization/SPINNAKER", "buildNumber": 9001, "buildInfoUrl": "http://spinnaker.travis.test.netflix.net/organization/SPINNAKER-package-echo/builds/69/", "buildHost": "http://spinnaker.travis.test.netflix.net/"]
  }

  @Unroll
  def "Legacy Jenkins detail from the url"() {

    when:
    buildDetailExtractor.tryToExtractJenkinsBuildDetails(buildInfo, result)

    then:
    result == expectedResult

    where:
    buildInfo                                                                                                          | result | expectedResult
    [name: "SPINNAKER-package-echo", "url": "http://spinnaker.jenkis.test.netflix.net/job/SPINNAKER-package-echo/69/"] | [:]    | ["job": "SPINNAKER-package-echo", "buildNumber": "69", "buildInfoUrl": "http://spinnaker.jenkis.test.netflix.net/job/SPINNAKER-package-echo/69/", "buildHost": "http://spinnaker.jenkis.test.netflix.net/"]
    [name: "SPINNAKER", "url": "http://spinnaker.jenkis.test.netflix.net/job/folderSPINNAKER/job/SPINNAKER/69/"]       | [:]    | ["job": "folderSPINNAKER/job/SPINNAKER", "buildNumber": "69", "buildInfoUrl": "http://spinnaker.jenkis.test.netflix.net/job/folderSPINNAKER/job/SPINNAKER/69/", "buildHost": "http://spinnaker.jenkis.test.netflix.net/"]
    [name: "job name", "url": "http://jenkins.com/job/folder/job/job name/123"]                                        | [:]    | ["job": "folder/job/job name", "buildNumber": "123", "buildInfoUrl": "http://jenkins.com/job/folder/job/job name/123", "buildHost": "http://jenkins.com/"]
  }

  @Unroll
  def "Extract detail, missing fields and edge cases"() {

    when:
    buildDetailExtractor.tryToExtractJenkinsBuildDetails(buildInfo, result)

    then:
    result == expectedResult

    where:
    buildInfo                                                                                           | result | expectedResult
    [name: "SPINNAKER", url: "http://spinnaker.jenkis.test.netflix.net/job/SPINNAKER-package-echo/69/"] | [:]    | ["job": "SPINNAKER-package-echo", "buildNumber": "69", "buildInfoUrl": "http://spinnaker.jenkis.test.netflix.net/job/SPINNAKER-package-echo/69/", "buildHost": "http://spinnaker.jenkis.test.netflix.net/"]
    [name: "compose/SPINNAKER", number: "9001", url: null]                                              | [:]    | [:]
    [number: "9001", artifacts: [], scm: []]                                                            | [:]    | [:]
    [:]                                                                                                 | [:]    | [:]
    null                                                                                                | [:]    | [:]
  }
}
