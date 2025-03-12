/*
 * Copyright 2019 Armory, Inc.
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

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException
import spock.lang.Specification

class PluginRepositoryServiceSpec extends Specification {
  String DEPLOYMENT = "default"
  HalconfigParserMocker mocker = new HalconfigParserMocker()

  LookupService getMockLookupService(String config) {
      def lookupService = new LookupService()
      lookupService.parser = mocker.mockHalconfigParser(config)
      return lookupService
  }

  PluginRepositoryService makePluginRepositoryService(String config) {
      def lookupService = getMockLookupService(config)
      def deploymentService = new DeploymentService()
      deploymentService.lookupService = lookupService
      new PluginRepositoryService(lookupService, new ValidateService(), deploymentService)
  }

  def "load an existing plugin repository node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  spinnaker:
    extensibility:
      repositories:
        test-plugin-repository:
          url: example.com
"""
    def pluginService = makePluginRepositoryService(config)

    when:
    def result = pluginService.getPluginRepositories(DEPLOYMENT)

    then:
    result != null
    result.size() == 1
    def pluginRepository = result.get("test-plugin-repository")
    pluginRepository.getUrl() == "example.com"

    when:
    result = pluginService.getPluginRepository(DEPLOYMENT, "test-plugin-repository")

    then:
    result != null
    result.getUrl() == "example.com"

    when:
    pluginService.getPluginRepository(DEPLOYMENT, 'non-existent-plugin')

    then:
    thrown(ConfigNotFoundException)
  }

  def "no error if plugin repositories is empty"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  spinnaker:
    extensibility:
      repositories: {}
"""
    def pluginService = makePluginRepositoryService(config)

    when:
    def result = pluginService.getPluginRepositories(DEPLOYMENT)

    then:
    result != null
    result.size() == 0

    when:
    pluginService.getPluginRepository(DEPLOYMENT, "test-plugin")

    then:
    thrown(ConfigNotFoundException)
  }

  def "no error if plugin is missing"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  spinnaker:
    extensibility:
      repositories:
"""
    def pluginService = makePluginRepositoryService(config)

    when:
    def result = pluginService.getPluginRepositories(DEPLOYMENT)

    then:
    result != null
    result.size() == 0

    when:

    pluginService.getPluginRepository(DEPLOYMENT, "test-template")

    then:
    thrown(ConfigNotFoundException)
  }

  def "multiple templates are correctly parsed"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  spinnaker:
    extensibility:
      repositories:
        test-repo-1:
          url: example.com
        test-repo-2:
          url: byah.org
"""
    def pluginService = makePluginRepositoryService(config)

    when:
    def result = pluginService.getPluginRepositories(DEPLOYMENT)

    then:
    result != null
    result.size() == 2

    when:
    result = pluginService.getPluginRepository(DEPLOYMENT, "test-repo-1")

    then:
    result != null
    result.getUrl() == "example.com"

    when:
    result = pluginService.getPluginRepository(DEPLOYMENT, "test-repo-2")

    then:
    result != null
    result.getUrl() == "byah.org"
  }
}
