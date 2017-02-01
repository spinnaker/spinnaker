/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.config.v1

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class HalconfigParserSpec extends Specification {
  String HALYARD_VERSION = "0.1.0"
  String SPINNAKER_VERSION = "1.0.0"
  String CURRENT_DEPLOYMENT = "my-spinnaker-deployment"
  HalconfigParser parser

  void setup() {
    parser = new HalconfigParser()
    parser.yamlParser = new Yaml()
    parser.objectMapper = new StrictObjectMapper()
  }

  void "Accept minimal config"() {
    setup:
    String config = """
halyardVersion: $HALYARD_VERSION
currentDeployment: $CURRENT_DEPLOYMENT
deploymentConfigurations:
- name: $CURRENT_DEPLOYMENT
  version: $SPINNAKER_VERSION
"""
    InputStream stream = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8))
    Halconfig out = null

    when:
    out = parser.parseHalconfig(stream)

    then:
    out.halyardVersion == HALYARD_VERSION
    out.currentDeployment == CURRENT_DEPLOYMENT
    out.deploymentConfigurations.size() == 1
    out.deploymentConfigurations[0].version == SPINNAKER_VERSION
  }

  void "Reject minimal config with typo"() {
    setup:
    String config = """
balyardVersion: $HALYARD_VERSION
"""
    InputStream stream = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8))
    Halconfig out = null

    when:
    out = parser.parseHalconfig(stream)

    then:
    UnrecognizedPropertyException ex = thrown()
    ex.message.contains("balyardVersion")
  }
}
