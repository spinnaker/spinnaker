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

import com.fasterxml.jackson.databind.ObjectMapper
import software.amazon.awssdk.services.codebuild.model.StartBuildRequest
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
    def builder = objectMapper.convertValue(request, StartBuildRequest.builder().getClass() as Class<StartBuildRequest.Builder>)
    def startBuildRequest = builder.build()

    then:
    startBuildRequest.projectName() == "test"
    startBuildRequest.environmentVariablesOverride().size() == 2
    startBuildRequest.environmentVariablesOverride().get(0).type().toString() == "PLAINTEXT"
    startBuildRequest.environmentVariablesOverride().get(0).name() == "KEY1"
    startBuildRequest.environmentVariablesOverride().get(0).value() == "VALUE1"
    startBuildRequest.environmentVariablesOverride().get(1).type().toString() == "PLAINTEXT"
    startBuildRequest.environmentVariablesOverride().get(1).name() == "KEY2"
    startBuildRequest.environmentVariablesOverride().get(1).value() == "VALUE2"

  }
}
