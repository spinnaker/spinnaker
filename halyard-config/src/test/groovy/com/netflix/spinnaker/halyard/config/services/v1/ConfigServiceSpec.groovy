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

package com.netflix.spinnaker.halyard.config.services.v1

import spock.lang.Specification

class ConfigServiceSpec extends Specification {
  String DEPLOYMENT = "default"
  HalconfigParserMocker mocker = new HalconfigParserMocker()

  ConfigService makeConfigService(String config) {
    def configService = new ConfigService()
    configService.halconfigParser = mocker.mockHalconfigParser(config)
    return configService
  }

  void "loads halconfig"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
"""
    def accountService = makeConfigService(config)

    when:
    def result = accountService.getConfig()

    then:
    result != null
  }

  void "loads current deployment"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
"""
    def accountService = makeConfigService(config)

    when:
    def result = accountService.getCurrentDeployment()

    then:
    result == DEPLOYMENT
  }
}
