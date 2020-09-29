/*
 * Copyright 2018 Google, Inc.
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

class WebhookServiceSpec extends Specification {
  String DEPLOYMENT = "default"
  HalconfigParserMocker mocker = new HalconfigParserMocker()
  LookupService lookupService = new LookupService()

  LookupService getMockLookupService(String config) {
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    return lookupService
  }

  WebhookService makeWebhookService(String config) {
    def lookupService = getMockLookupService(config)
    def deploymentService = new DeploymentService()
    deploymentService.lookupService = lookupService

    new WebhookService(lookupService, deploymentService, new ValidateService())
  }

  def "load existing webhook node with custom trust disabled"() {
        setup:
        String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  webhook:
    trust:
      enabled: false
"""
        def webhookService = makeWebhookService(config)

        when:
        def result = webhookService.getWebhook(DEPLOYMENT)

        then:
        result != null
        result.getTrust() != null
        !result.getTrust().isEnabled()
    }

    def "load existing webhook node with custom trust enabled"() {
        setup:
        String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  webhook:
    trust:
      enabled: true
      trustStore: /home/user/keystore.jks
      trustStorePassword: password
"""
        def webhookService = makeWebhookService(config)

        when:
        def result = webhookService.getWebhook(DEPLOYMENT)

        then:
        result != null
        result.getTrust() != null
        result.getTrust().isEnabled()
        result.getTrust().getTrustStore() == "/home/user/keystore.jks"
        result.getTrust().getTrustStorePassword() == "password"
    }

  def "load a non-existent webhook node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
"""
    def webhookService = makeWebhookService(config)

    when:
    def result = webhookService.getWebhook(DEPLOYMENT)

    then:
    result != null
    result.getTrust() != null
    !result.getTrust().isEnabled()
  }

  def "load a non-default deployment's webhook node"() {
    setup:
    def deployment2 = "non-default"
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  webhook:
    trust:
      enabled: true
      trustStore: /home/user/keystore.jks
      trustStorePassword: password
- name: $deployment2
  version: 1
  providers: null
  webhook:
    trust:
      enabled: true
      trustStore: /home/user/keystore2.jks
      trustStorePassword: password2
"""
    def webhookService = makeWebhookService(config)

    when:
    def result = webhookService.getWebhook(DEPLOYMENT)

    then:
    result != null
    result.getTrust() != null
    result.getTrust().isEnabled()
    result.getTrust().getTrustStore() == "/home/user/keystore.jks"
    result.getTrust().getTrustStorePassword() == "password"

    when:
    result = webhookService.getWebhook(deployment2)

    then:
    result != null
    result.getTrust() != null
    result.getTrust().isEnabled()
    result.getTrust().getTrustStore() == "/home/user/keystore2.jks"
    result.getTrust().getTrustStorePassword() == "password2"
  }
}
