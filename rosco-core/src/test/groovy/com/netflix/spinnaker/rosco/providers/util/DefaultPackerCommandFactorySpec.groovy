/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.providers.util

import spock.lang.Shared
import spock.lang.Specification

class DefaultPackerCommandFactorySpec extends Specification {

  @Shared
  DefaultPackerCommandFactory packerCommandFactory = new DefaultPackerCommandFactory()

  @Shared
  String templateFile = "some-packer-template.json"

  void "packer command is built properly with multiple parameters"() {
    setup:
      def parameterMap = [
        some_project_id:   "some-project",
        some_zone:         "us-central1-a",
        some_source_image: "ubuntu-1404-trusty-v20141212",
        some_target_image: "some-new-image"
      ]

    when:
      def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, templateFile)

    then:
      packerCommand == ["sh", "-c", "packer build -color=false -var some_project_id=some-project " +
                                    "-var some_zone=us-central1-a -var some_source_image=ubuntu-1404-trusty-v20141212 " +
                                    "-var some_target_image=some-new-image " +
                                    "some-packer-template.json"]
  }

  void "packer command is built properly with zero parameters"() {
    setup:
      def parameterMap = [:]

    when:
      def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, templateFile)

    then:
      packerCommand == ["sh", "-c", "packer build -color=false some-packer-template.json"]
  }

  void "packer command elides parameters with empty keys"() {
    setup:
      def parameterMap = [
        "": "some-value-2",
        some_key_3: "some-value-3"
      ]
      parameterMap[null] = "some-value-1"

    when:
      def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, templateFile)

    then:
      packerCommand == ["sh", "-c", "packer build -color=false -var some_key_3=some-value-3 some-packer-template.json"]
  }

  void "packer command elides parameters with empty values"() {
    when:
      def parameterMap = [
        some_key_1: "some-value",
        some_key_2: null
      ]
      def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, templateFile)

    then:
      packerCommand == ["sh", "-c", "packer build -color=false -var some_key_1=some-value some-packer-template.json"]

    when:
      parameterMap = [
        some_key_1: "some-value",
        some_key_2: ""
      ]
      packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, templateFile)

    then:
      packerCommand == ["sh", "-c", "packer build -color=false -var some_key_1=some-value some-packer-template.json"]
  }

  void "packer command quotes parameters with values that contain spaces"() {
    when:
      def parameterMap = [
        some_key_1: "some-value",
        some_key_2: "some set of values"
      ]
      def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, templateFile)

    then:
      packerCommand == ["sh", "-c", "packer build -color=false -var some_key_1=some-value " +
                                    "-var \"some_key_2=some set of values\" some-packer-template.json"]
  }

  void "packer command prepends base command when specified"() {
    when:
      def parameterMap = [
        some_key_1: "some-value",
        some_key_2: "some set of values"
      ]
      def packerCommand =
        packerCommandFactory.buildPackerCommand("some base command ; ", parameterMap, templateFile)

    then:
      packerCommand == ["sh", "-c", "some base command ; packer build -color=false -var some_key_1=some-value " +
                                    "-var \"some_key_2=some set of values\" some-packer-template.json"]
  }

}
