/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1

import spock.lang.Specification

class SpinnakerArtifactSpec extends Specification {
  void "correctly match all required profiles for a spring component"() {
    setup:
    String correctPath = "correct"
    String incorrectPath = "correct"

    File clouddriver1 = Mock(File)
    File clouddriver2 = Mock(File)
    File echo1 = Mock(File)
    File echo2 = Mock(File)
    File spinnaker = Mock(File)

    clouddriver1.getAbsolutePath() >> correctPath
    clouddriver1.getName() >> "clouddriver.yml"

    clouddriver2.getAbsolutePath() >> correctPath
    clouddriver2.getName() >> "clouddriver-profile.yml"

    echo1.getAbsolutePath() >> incorrectPath
    echo1.getName() >> "echo.yml"

    echo2.getAbsolutePath() >> incorrectPath
    echo2.getName() >> "echo-profile.yml"

    spinnaker.getAbsolutePath() >> correctPath
    spinnaker.getName() >> "spinnaker.yml"

    File[] files = [clouddriver1, clouddriver2, echo1, echo2, spinnaker]

    when:
    def result = (SpinnakerArtifact.CLOUDDRIVER).profilePaths(files)

    then:
    result.every { String t -> t.equals(correctPath) }
  }
}
