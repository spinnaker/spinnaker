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

import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig
import org.springframework.context.ApplicationContext
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

class HalconfigParserSpec extends Specification {
  String HALYARD_VERSION = "0.1.0"
  String SPINNAKER_VERSION = "1.0.0"
  String CURRENT_DEPLOYMENT = "my-spinnaker-deployment"
  HalconfigParser parser

  void setup() {
    ApplicationContext applicationContext = Stub(ApplicationContext.class)
    applicationContext.getBean(Yaml.class) >> new Yaml(new SafeConstructor())
    parser = new HalconfigParser()
    parser.applicationContext = applicationContext
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
    IllegalArgumentException ex = thrown()
    ex.message.contains("balyardVersion")
  }

  void "parses deployment location"() {
    setup:
    String config = """
deploymentConfigurations:
- deploymentEnvironment:
    location: myLocation
"""
    InputStream stream = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8))
    Halconfig out = null

    when:
    out = parser.parseHalconfig(stream)

    then:
    out.deploymentConfigurations[0].deploymentEnvironment.location == 'myLocation'
  }

  void "parses deployment custom sizings"() {
    setup:
    String config = """
deploymentConfigurations:
- deploymentEnvironment:
    customSizing:
      clouddriver:
        requests:
          memory: 64Mi
          cpu: 250m
        limits:
         memory: 128Mi
         cpu: 500m
      echo:
        requests:
         memory: 128Mi
         cpu: 500m
        limits:
          memory: 64Mi
          cpu: 250m
"""
    InputStream stream = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8))
    Halconfig out = null

    when:
    out = parser.parseHalconfig(stream)

    then:
    out.deploymentConfigurations[0].deploymentEnvironment.customSizing['clouddriver'].requests.memory == '64Mi'
    out.deploymentConfigurations[0].deploymentEnvironment.customSizing['clouddriver'].requests.cpu == '250m'
    out.deploymentConfigurations[0].deploymentEnvironment.customSizing['clouddriver'].limits.memory == '128Mi'
    out.deploymentConfigurations[0].deploymentEnvironment.customSizing['clouddriver'].limits.cpu == '500m'
    out.deploymentConfigurations[0].deploymentEnvironment.customSizing['echo'].limits.memory == '64Mi'
    out.deploymentConfigurations[0].deploymentEnvironment.customSizing['echo'].limits.cpu == '250m'
    out.deploymentConfigurations[0].deploymentEnvironment.customSizing['echo'].requests.memory == '128Mi'
    out.deploymentConfigurations[0].deploymentEnvironment.customSizing['echo'].requests.cpu == '500m'
  }

  @Unroll("parses authn: #authnProvider:#propertyName value should be #propertyValue")
  void "parses all authn properties"() {
    setup:
    String config = """
deploymentConfigurations:
- security:
    authn:
      ${authnProvider}:
        ${propertyName}: ${propertyValue}
"""
    InputStream stream = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8))
    Halconfig out = null

    when:
    out = parser.parseHalconfig(stream)

    then:
    out.deploymentConfigurations[0].security.authn[authnProvider][propertyName] == propertyValue

    where:
    authnProvider | propertyName            | propertyValue
    "saml"        | "enabled"               | true
    "saml"        | "issuerId"              | "myIssuer"
    "saml"        | "keyStore"              | "/my/key/store"
    "x509"        | "enabled"               | true
    "x509"        | "roleOid"               | "1.2.3.4.5"
    "x509"        | "subjectPrincipalRegex" | ".*"
    // Uncomment the below to implement LDAP. Then fill in the rest of the LDAP properties, one per line.
//    "ldap"        | "enabled"    | true
  }
}
