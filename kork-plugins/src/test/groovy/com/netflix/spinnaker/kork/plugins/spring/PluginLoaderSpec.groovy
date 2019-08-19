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
import com.netflix.spinnaker.kork.plugins.spring.configs.PluginConfiguration
import com.netflix.spinnaker.kork.plugins.spring.configs.PluginProperties
import com.netflix.spinnaker.kork.plugins.spring.configs.PluginPropertyDetails
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Paths

class PluginLoaderSpec extends Specification {

  ByteArrayInputStream inputStream
  ObjectMapper objectMapper
  PluginProperties pluginProperties
  PluginPropertyDetails pluginPropertyDetails
  ArrayList<PluginConfiguration> pluginConfigurationArrayList

  @Subject
  PluginLoader subject

  def setup() {
    objectMapper = new ObjectMapper(new YAMLFactory());
  }

  def "should return enabled plugin paths"() {
    given:
    subject = new PluginLoader(null)
    PluginConfiguration pluginConfiguration1 = new PluginConfiguration("namespace/p1", ["/p1/path"], plugin1Enabled)
    PluginConfiguration pluginConfiguration2 = new PluginConfiguration("namespace/p2", ["/p2/path"], plugin2Enabled)

    when:
    pluginConfigurationArrayList = [ pluginConfiguration1, pluginConfiguration2 ]
    pluginProperties = new PluginProperties(new PluginPropertyDetails( pluginConfigurationArrayList, true))
    pluginPropertyDetails = pluginProperties.getPluginsPropertyDetails()


    then:
    subject.getEnabledJars(pluginPropertyDetails) == expected

    where:
    plugin1Enabled | plugin2Enabled | expected
    true           | false          | [new PluginConfiguration("namespace/p1", ["/p1/path"], plugin1Enabled)]
    true           | true           | [new PluginConfiguration("namespace/p1", ["/p1/path"], plugin1Enabled),
                                       new PluginConfiguration("namespace/p2", ["/p2/path"], plugin2Enabled)]
  }

  def "can parse plugin configs"() {
    given:
    subject = new PluginLoader(null)

    when:
    def config = [
      plugins: [
        pluginConfigurations: [
          [name: 'namespace/p1', jars: ["/p1/path"], enabled: plugin1Enabled],
          [name: 'namespace/p2', jars: ["/p2/path"], enabled: plugin2Enabled],
        ],
        downloadingEnabled: true
      ]
    ]
    def configAsYaml = new Yaml().dump(config)
    inputStream = new ByteArrayInputStream(configAsYaml.getBytes())
    pluginConfigurationArrayList = [
      new PluginConfiguration("namespace/p1", ["/p1/path"], plugin1Enabled),
      new PluginConfiguration("namespace/p2", ["/p2/path"], plugin2Enabled)
    ]
    pluginProperties = new PluginProperties(new PluginPropertyDetails(pluginConfigurationArrayList, true))
    pluginPropertyDetails = pluginProperties.getPluginsPropertyDetails()

    then:
    subject.parsePluginConfigs(inputStream) == pluginPropertyDetails

    where:
    plugin1Enabled | plugin2Enabled
    true           | false
    true           | true
  }

  def "should only add a jar to the classpath once"() {
    given:
    subject = new PluginLoader(null)

    when:
    PluginConfiguration pluginConfiguration1 = new PluginConfiguration("namespace/p1", plugin1Jars, true)
    PluginConfiguration pluginConfiguration2 = new PluginConfiguration("namespace/p2", plugin2Jars, true)
    pluginConfigurationArrayList = [ pluginConfiguration1, pluginConfiguration2 ]
    pluginProperties = new PluginProperties(new PluginPropertyDetails( pluginConfigurationArrayList, true))
    pluginPropertyDetails = pluginProperties.getPluginsPropertyDetails()

    then:
    subject.getJarPathsFromPluginConfigurations(pluginPropertyDetails.getPluginConfigurations(), true) == expected

    where:
    plugin1Jars | plugin2Jars | expected
    ["/foo/bar"] | ["/bar/baz"] | [Paths.get("/foo/bar").toUri().toURL(), Paths.get("/bar/baz").toUri().toURL()]
    ["/foo/bar"] | ["/foo/bar"] | [Paths.get("/foo/bar").toUri().toURL()]
  }

  def "Plugin names should only be unique"() {
    when:
    subject = new PluginLoader(null)
    PluginConfiguration pluginConfiguration1 = new PluginConfiguration("foo/bar", [], true)
    PluginConfiguration pluginConfiguration2 = new PluginConfiguration("foo/bar", [], true)
    pluginConfigurationArrayList = [ pluginConfiguration1, pluginConfiguration2 ]
    pluginProperties = new PluginProperties(new PluginPropertyDetails( pluginConfigurationArrayList, true))
    pluginPropertyDetails = pluginProperties.getPluginsPropertyDetails()
    pluginPropertyDetails.validate()

    then:
    thrown MalformedPluginConfigurationException
  }

  def "should be able to get URL from path"() {
    when:
    subject = new PluginLoader()

    then:
    subject.convertToUrl(jarLocation, true) == expected

    where:
    jarLocation                               | expected
    "/opt/spinnaker/plugin/foo-bar-1.2.3.jar" | Paths.get("/opt/spinnaker/plugin/foo-bar-1.2.3.jar").toUri().toURL()
    "file://example/com/foo-bar-1.2.3.jar"    | new URL("file://example/com/foo-bar-1.2.3.jar")
    "http://example.com/foo-bar-1.2.3.jar"    | new URL("http://example.com/foo-bar-1.2.3.jar")
  }

  def "should throw exception if not a valid JAR location"() {
    when:
    subject = new PluginLoader()
    subject.convertToUrl(jarLocation, true)

    then:
    thrown(thrownException)

    where:
    jarLocation | thrownException
    "foobar"    | MalformedPluginConfigurationException
    null        | MalformedPluginConfigurationException
  }


  def "should use local jars if downloading is disabled "() {
    when:
    subject = new PluginLoader()

    then:
    subject.convertToUrl(jarLocation, false) == expected

    where:
    jarLocation                               | expected
    "/opt/spinnaker/plugin/foo-bar-1.2.3.jar" | Paths.get("/opt/spinnaker/plugin/foo-bar-1.2.3.jar").toUri().toURL()
    "file://example/com/foo-bar-1.2.3.jar"    | new URL("file://example/com/foo-bar-1.2.3.jar")
  }

  def "should throw exception if trying to download a jar when not enabled"() {
    when:
    subject = new PluginLoader()
    subject.convertToUrl("http://example.com/foo-bar-1.2.3.jar", false)

    then:
    thrown(MalformedPluginConfigurationException)
  }

}
