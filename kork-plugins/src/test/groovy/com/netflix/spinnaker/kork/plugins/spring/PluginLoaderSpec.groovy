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

package com.netflix.spinnaker.kork.plugins.spring

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Paths

class PluginLoaderSpec extends Specification {

  ByteArrayInputStream inputStream
  ObjectMapper objectMapper
  PluginProperties pluginProperties
  ArrayList<PluginProperties.PluginConfiguration> pluginConfigurationArrayList

  @Subject
  PluginLoader subject

  def setup() {
    objectMapper = new ObjectMapper(new YAMLFactory());
  }

  def "should return enabled plugin paths"() {
    given:
    subject = new PluginLoader(null)
    PluginProperties.PluginConfiguration pluginConfiguration1 = new PluginProperties.PluginConfiguration("namespace/p1", ["p1/path"], plugin1Enabled)
    PluginProperties.PluginConfiguration pluginConfiguration2 = new PluginProperties.PluginConfiguration("namespace/p2", ["p2/path"], plugin2Enabled)

    when:
    pluginConfigurationArrayList = [ pluginConfiguration1, pluginConfiguration2 ]
    pluginProperties = new PluginProperties(pluginConfigurationArrayList)

    then:
    subject.getEnabledJars(pluginProperties) == expected

    where:
    plugin1Enabled | plugin2Enabled | expected
    true           | false          | [new PluginProperties.PluginConfiguration("namespace/p1", ["p1/path"], plugin1Enabled)]
    true           | true           | [new PluginProperties.PluginConfiguration("namespace/p1", ["p1/path"], plugin1Enabled),
                                       new PluginProperties.PluginConfiguration("namespace/p2", ["p2/path"], plugin2Enabled)]
  }

  def "can parse plugin configs"() {
    given:
    subject = new PluginLoader(null)

    when:
    def config = [
            plugins: [
              [name: 'namespace/p1', jars: ["p1/path"], enabled: plugin1Enabled],
              [name: 'namespace/p2', jars: ['p2/path'], enabled: plugin2Enabled],
            ]
    ]
    def configAsYaml = new Yaml().dump(config)
    inputStream = new ByteArrayInputStream(configAsYaml.getBytes())
    pluginConfigurationArrayList = [
      new PluginProperties.PluginConfiguration("namespace/p1", ["p1/path"], plugin1Enabled),
      new PluginProperties.PluginConfiguration("namespace/p2", ["p2/path"], plugin2Enabled)
    ]
    pluginProperties = new PluginProperties(pluginConfigurationArrayList)

    then:
    subject.parsePluginConfigs(inputStream) == pluginProperties

    where:
    plugin1Enabled | plugin2Enabled
    true           | false
    true           | true
  }

  def "should only add a jar to the classpath once"() {
    given:
    subject = new PluginLoader(null)

    when:
    PluginProperties.PluginConfiguration pluginConfiguration1 = new PluginProperties.PluginConfiguration("namespace/p1", plugin1Jars, true)
    PluginProperties.PluginConfiguration pluginConfiguration2 = new PluginProperties.PluginConfiguration("namespace/p2", plugin2Jars, true)
    pluginConfigurationArrayList = [ pluginConfiguration1, pluginConfiguration2 ]
    pluginProperties = new PluginProperties(pluginConfigurationArrayList)

    then:
    subject.getJarPathsFromPluginConfigurations(pluginProperties.pluginConfigurationList) == expected

    where:
    plugin1Jars | plugin2Jars | expected
    ["foo/bar"] | ["bar/baz"] | [Paths.get("foo/bar").toUri().toURL(), Paths.get("bar/baz").toUri().toURL()]
    ["foo/bar"] | ["foo/bar"] | [Paths.get("foo/bar").toUri().toURL()]
  }

  def "Plugin names should only be unique"() {
    when:
    subject = new PluginLoader(null)
    PluginProperties.PluginConfiguration pluginConfiguration1 = new PluginProperties.PluginConfiguration("foo/bar", [], true)
    PluginProperties.PluginConfiguration pluginConfiguration2 = new PluginProperties.PluginConfiguration("foo/bar", [], true)
    pluginConfigurationArrayList = [ pluginConfiguration1, pluginConfiguration2 ]
    pluginProperties = new PluginProperties(pluginConfigurationArrayList)
    pluginProperties.validate()

    then:
    thrown MalformedPluginConfigurationException
  }



}
