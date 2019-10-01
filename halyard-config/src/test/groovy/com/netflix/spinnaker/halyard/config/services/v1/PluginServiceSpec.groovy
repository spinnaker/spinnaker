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

class PluginServiceSpec extends Specification {
  final String DEPLOYMENT = "default"
  final HalconfigParserMocker mocker = new HalconfigParserMocker()

  LookupService getMockLookupService(String config) {
      def lookupService = new LookupService()
      lookupService.parser = mocker.mockHalconfigParser(config)
      return lookupService
  }

  PluginService makePluginService(String config) {
      def lookupService = getMockLookupService(config)
      def deploymentService = new DeploymentService()
      deploymentService.lookupService = lookupService
      new PluginService(lookupService, new ValidateService(), deploymentService)
  }

  def "load an existing plugin node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  plugins:
    plugins:
    - name: test-plugin
      manifestLocation: /home/user/test-plugin.yaml
      options:
        foo: bar
        nested:
          key: value
"""
    def pluginService = makePluginService(config)

    when:
    def result = pluginService.getAllPlugins(DEPLOYMENT)

    then:
    result != null
    result.size() == 1
    result[0].getName() == "test-plugin"
    result[0].getManifestLocation() == "/home/user/test-plugin.yaml"
    result[0].getOptions().get("foo") == "bar"
    result[0].getOptions().get("nested").get("key") == "value"

    when:
    result = pluginService.getPlugin(DEPLOYMENT, "test-plugin")

    then:
    result != null
    result.getName() == "test-plugin"
    result.getManifestLocation() == "/home/user/test-plugin.yaml"

    when:
    pluginService.getPlugin(DEPLOYMENT, 'non-existent-plugin')

    then:
    thrown(ConfigNotFoundException)
  }

  def "no error if plugin is empty"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  plugins:
    plugins: []
"""
    def pluginService = makePluginService(config)

    when:
    def result = pluginService.getAllPlugins(DEPLOYMENT)

    then:
    result != null
    result.size() == 0

    when:
    pluginService.getPlugin(DEPLOYMENT, "test-plugin")

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
  plugins:
"""
    def pluginService = makePluginService(config)

    when:
    def result = pluginService.getAllPlugins(DEPLOYMENT)

    then:
    result != null
    result.size() == 0

    when:

    pluginService.getPlugin(DEPLOYMENT, "test-template")

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
  plugins:
    plugins:
    - name: test-plugin
      manifestLocation: /home/user/test-plugin.yaml
    - name: test-plugin-2
      manifestLocation: /home/user/test-plugin-2.yaml
"""
    def pluginService = makePluginService(config)

    when:
    def result = pluginService.getAllPlugins(DEPLOYMENT)

    then:
    result != null
    result.size() == 2

    when:
    result = pluginService.getPlugin(DEPLOYMENT, "test-plugin")

    then:
    result != null
    result.getName() == "test-plugin"
    result.getManifestLocation() == "/home/user/test-plugin.yaml"

    when:
    result = pluginService.getPlugin(DEPLOYMENT, "test-plugin-2")

    then:
    result != null
    result.getName() == "test-plugin-2"
    result.getManifestLocation() == "/home/user/test-plugin-2.yaml"
  }
}
