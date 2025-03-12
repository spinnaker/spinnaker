/*
 * Copyright 2020 Amazon.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.igor.codebuild

import com.amazonaws.services.codebuild.model.StartBuildRequest
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification
import spock.lang.Subject

class AwsCodeBuildControllerSpec extends Specification {

  @Subject
  ObjectMapper objectMapper = new ObjectMapper();

  def "objectMapper should convert a map to a StartBuildRequest"() {
    given:
    def request = [
      projectName: "test",
      environmentVariablesOverride: [
        [
          type: "PLAINTEXT",
          name: "KEY1",
          value: "VALUE1",
        ],
        [
          type: "PLAINTEXT",
          name: "KEY2",
          value: "VALUE2",
        ],
      ]
    ]

    when:
    def startBuildRequest = objectMapper.convertValue(request, StartBuildRequest.class)

    then:
    startBuildRequest.getProjectName() == "test"
    startBuildRequest.getEnvironmentVariablesOverride().size() == 2
    startBuildRequest.getEnvironmentVariablesOverride().get(0).getType() == "PLAINTEXT"
    startBuildRequest.getEnvironmentVariablesOverride().get(0).getName() == "KEY1"
    startBuildRequest.getEnvironmentVariablesOverride().get(0).getValue() == "VALUE1"
    startBuildRequest.getEnvironmentVariablesOverride().get(1).getType() == "PLAINTEXT"
    startBuildRequest.getEnvironmentVariablesOverride().get(1).getName() == "KEY2"
    startBuildRequest.getEnvironmentVariablesOverride().get(1).getValue() == "VALUE2"

  }
}
