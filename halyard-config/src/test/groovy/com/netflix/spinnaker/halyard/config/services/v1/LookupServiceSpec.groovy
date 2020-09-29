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

import com.netflix.spinnaker.halyard.config.model.v1.node.Account
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider
import spock.lang.Specification

class LookupServiceSpec extends Specification {
  static String DEPLOYMENT_NAME = "default"
  static String KUBERNETES_ACCOUNT_NAME = "my-k8s-account"
  static String DOCKER_REGISTRY_ACCOUNT_NAME = "my-docker-account"
  static String GOOGLE_ACCOUNT_NAME = "my-google-account"
  static String KUBERNETES_PROVIDER = "kubernetes"
  static String DOCKER_REGISTRY_PROVIDER = "dockerRegistry"
  static String GOOGLE_PROVIDER = "google"
  static String AZURE_ACCOUNT_NAME = "my-azure-account"
  static String AZURE_PROVIDER = "azure"
  HalconfigParserMocker mocker = new HalconfigParserMocker()

  def "find a kubernetes account"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT_NAME
deploymentConfigurations:
- name: $DEPLOYMENT_NAME
  version: 1
  providers:
    $KUBERNETES_PROVIDER:
      enabled: true
      accounts:
        - name: $KUBERNETES_ACCOUNT_NAME
"""
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .setProvider(KUBERNETES_PROVIDER)
        .setAccount(KUBERNETES_ACCOUNT_NAME)

    when:
    def result = lookupService.getMatchingNodesOfType(filter, Account.class)

    then:
    !result.isEmpty()
    result[0].nodeName == KUBERNETES_ACCOUNT_NAME
    result.size() == 1
  }

  def "find a kubernetes account among two"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT_NAME
deploymentConfigurations:
- name: $DEPLOYMENT_NAME
  version: 1
  providers:
    $KUBERNETES_PROVIDER:
      enabled: true
      accounts:
        - name: $KUBERNETES_ACCOUNT_NAME
        - name: $KUBERNETES_ACCOUNT_NAME-1
"""
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .setProvider(KUBERNETES_PROVIDER)
        .setAccount(KUBERNETES_ACCOUNT_NAME)

    when:
    def result = lookupService.getMatchingNodesOfType(filter, Account.class)

    then:
    !result.isEmpty()
    result[0].nodeName == KUBERNETES_ACCOUNT_NAME
    result.size() == 1
  }

  def "find no kubernetes account due to wrong deployment"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT_NAME
deploymentConfigurations:
- name: $DEPLOYMENT_NAME-1
  version: 1
  providers:
    $KUBERNETES_PROVIDER:
      enabled: true
      accounts:
        - name: $KUBERNETES_ACCOUNT_NAME
"""
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .setProvider(KUBERNETES_PROVIDER)
        .setAccount(KUBERNETES_ACCOUNT_NAME)

    when:
    def result = lookupService.getMatchingNodesOfType(filter, Account.class)

    then:
    result.isEmpty()
  }

  def "find a dockerRegistry account"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT_NAME
deploymentConfigurations:
- name: $DEPLOYMENT_NAME
  version: 1
  providers:
    $KUBERNETES_PROVIDER:
      enabled: true
      accounts:
        - name: $KUBERNETES_ACCOUNT_NAME
    $DOCKER_REGISTRY_PROVIDER:
      enabled: true
      accounts:
        - name: $DOCKER_REGISTRY_ACCOUNT_NAME
"""
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .setProvider(DOCKER_REGISTRY_PROVIDER)
        .setAccount(DOCKER_REGISTRY_ACCOUNT_NAME)

    when:
    def result = lookupService.getMatchingNodesOfType(filter, Account.class)

    then:
    !result.isEmpty()
    result[0].nodeName == DOCKER_REGISTRY_ACCOUNT_NAME
    result.size() == 1
  }

  def "find a google account"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT_NAME
deploymentConfigurations:
- name: $DEPLOYMENT_NAME
  version: 1
  providers:
    $KUBERNETES_PROVIDER:
      enabled: true
      accounts:
        - name: $KUBERNETES_ACCOUNT_NAME
    $DOCKER_REGISTRY_PROVIDER:
      enabled: true
      accounts:
        - name: $DOCKER_REGISTRY_ACCOUNT_NAME
    $GOOGLE_PROVIDER:
      enabled: true
      accounts:
        - name: $GOOGLE_ACCOUNT_NAME
"""
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .setProvider(GOOGLE_PROVIDER)
        .setAccount(GOOGLE_ACCOUNT_NAME)

    when:
    def result = lookupService.getMatchingNodesOfType(filter, Account.class)

    then:
    !result.isEmpty()
    result[0].nodeName == GOOGLE_ACCOUNT_NAME
    result.size() == 1
  }

  def "find no dockerRegistry account in the kubernetes provider"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT_NAME
deploymentConfigurations:
- name: $DEPLOYMENT_NAME
  version: 1
  providers:
    $KUBERNETES_PROVIDER:
      enabled: true
      accounts:
        - name: $KUBERNETES_ACCOUNT_NAME
    $DOCKER_REGISTRY_PROVIDER:
      enabled: true
      accounts:
        - name: $DOCKER_REGISTRY_ACCOUNT_NAME
"""
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .setProvider(KUBERNETES_PROVIDER)
        .setAccount(DOCKER_REGISTRY_ACCOUNT_NAME)

    when:
    def result = lookupService.getMatchingNodesOfType(filter, Account.class)

    then:
    result.isEmpty()
  }

  def "find a disabled dockerRegistry account"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT_NAME
deploymentConfigurations:
- name: $DEPLOYMENT_NAME
  version: 1
  providers:
    $KUBERNETES_PROVIDER:
      enabled: true
      accounts:
        - name: $KUBERNETES_ACCOUNT_NAME
    $DOCKER_REGISTRY_PROVIDER:
      enabled: false
      accounts:
        - name: $DOCKER_REGISTRY_ACCOUNT_NAME
"""
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .setProvider(DOCKER_REGISTRY_PROVIDER)
        .setAccount(DOCKER_REGISTRY_ACCOUNT_NAME)

    when:
    def result = lookupService.getMatchingNodesOfType(filter, Account.class)

    then:
    !result.isEmpty()
    result[0].nodeName == DOCKER_REGISTRY_ACCOUNT_NAME
    result.size() == 1
  }

  def "find multiple dockerRegistry accounts"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT_NAME
deploymentConfigurations:
- name: $DEPLOYMENT_NAME
  version: 1
  providers:
    $KUBERNETES_PROVIDER:
      enabled: true
      accounts:
        - name: $KUBERNETES_ACCOUNT_NAME
    $DOCKER_REGISTRY_PROVIDER:
      enabled: true
      accounts:
        - name: $DOCKER_REGISTRY_ACCOUNT_NAME
        - name: $DOCKER_REGISTRY_ACCOUNT_NAME-1
"""
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .setProvider(DOCKER_REGISTRY_PROVIDER)
        .withAnyAccount()

    when:
    def result = lookupService.getMatchingNodesOfType(filter, Account.class)

    then:
    !result.isEmpty()
    result.size() == 2
    result[0].getNodeName() != result[1].getNodeName()
  }

  def "find multiple accounts"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT_NAME
deploymentConfigurations:
- name: $DEPLOYMENT_NAME
  version: 1
  providers:
    $KUBERNETES_PROVIDER:
      enabled: true
      accounts:
        - name: $KUBERNETES_ACCOUNT_NAME
    $DOCKER_REGISTRY_PROVIDER:
      enabled: true
      accounts:
        - name: $DOCKER_REGISTRY_ACCOUNT_NAME
    $GOOGLE_PROVIDER:
      enabled: true
      accounts:
        - name: $GOOGLE_ACCOUNT_NAME
    $AZURE_PROVIDER:
      enabled: true
      accounts:
        - name: $AZURE_ACCOUNT_NAME
"""
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .withAnyProvider()
        .withAnyAccount()

    when:
    def result = lookupService.getMatchingNodesOfType(filter, Account.class)

    then:
    !result.isEmpty()
    result.size() == 4
    result[0].getNodeName() != result[1].getNodeName()
    result[1].getNodeName() != result[2].getNodeName()
    result[0].getNodeName() != result[2].getNodeName()
    result[0].getNodeName() != result[3].getNodeName()
    result[1].getNodeName() != result[3].getNodeName()
    result[2].getNodeName() != result[3].getNodeName()
  }

  def "find kubernetes provider"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT_NAME
deploymentConfigurations:
- name: $DEPLOYMENT_NAME
  version: 1
  providers:
    $KUBERNETES_PROVIDER:
      enabled: true
      accounts:
        - name: $KUBERNETES_ACCOUNT_NAME
    $DOCKER_REGISTRY_PROVIDER:
      enabled: true
      accounts:
        - name: $DOCKER_REGISTRY_ACCOUNT_NAME
    $GOOGLE_PROVIDER:
      enabled: true
      accounts:
        - name: $GOOGLE_ACCOUNT_NAME
"""
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .setProvider(KUBERNETES_PROVIDER)

    when:
    def result = lookupService.getMatchingNodesOfType(filter, Provider.class)

    then:
    !result.isEmpty()
    result[0].getNodeName() == KUBERNETES_PROVIDER
    result.size() == 1
  }

  def "find an azure account"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT_NAME
deploymentConfigurations:
- name: $DEPLOYMENT_NAME
  version: 1
  providers:
    $KUBERNETES_PROVIDER:
      enabled: true
      accounts:
        - name: $KUBERNETES_ACCOUNT_NAME
    $DOCKER_REGISTRY_PROVIDER:
      enabled: true
      accounts:
        - name: $DOCKER_REGISTRY_ACCOUNT_NAME
    $GOOGLE_PROVIDER:
      enabled: true
      accounts:
        - name: $GOOGLE_ACCOUNT_NAME
    $AZURE_PROVIDER:
      enabled: true
      accounts:
        - name: $AZURE_ACCOUNT_NAME
"""
    def lookupService = new LookupService()
    lookupService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .setProvider(AZURE_PROVIDER)
        .setAccount(AZURE_ACCOUNT_NAME)

    when:
    def result = lookupService.getMatchingNodesOfType(filter, Account.class)

    then:
    !result.isEmpty()
    result[0].nodeName == AZURE_ACCOUNT_NAME
    result.size() == 1
  }
}
