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

import com.netflix.spinnaker.halyard.core.error.v1.HalException
import spock.lang.Specification

class AccountServiceSpec extends Specification {
  String DEPLOYMENT = "default"
  String PROVIDER = "kubernetes"
  String PROVIDER2 = "google"
  String ACCOUNT_NAME = "my-account"
  HalconfigParserMocker mocker = new HalconfigParserMocker()

  AccountService makeAccountService(String config) {
    def lookupService = new LookupService()
    def providerService = new ProviderService()
    def accountService = new AccountService()

    lookupService.parser = mocker.mockHalconfigParser(config)
    providerService.lookupService = lookupService

    accountService.lookupService = lookupService
    accountService.providerService = providerService
    return accountService
  }

  def "load an existent account node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: 
    $PROVIDER:
      enabled: true
      accounts:
        - name: $ACCOUNT_NAME
"""
    def accountService = makeAccountService(config)

    when:
    def result = accountService.getProviderAccount(DEPLOYMENT, PROVIDER, ACCOUNT_NAME)

    then:
    result != null
    result.getName() == ACCOUNT_NAME
  }

  def "fail to load a non-existent account node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: 
    $PROVIDER:
      enabled: true
      accounts:
        - name: $ACCOUNT_NAME-2
"""
    def accountService = makeAccountService(config)

    when:
    accountService.getProviderAccount(DEPLOYMENT, PROVIDER, ACCOUNT_NAME)

    then:
    HalException ex = thrown()
    ex.problems.problems[0].message.contains("No account with name")
  }

  def "fail to load a duplicate account node in one provider"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: 
    $PROVIDER:
      enabled: true
      accounts:
        - name: $ACCOUNT_NAME
        - name: $ACCOUNT_NAME
"""
    def accountService = makeAccountService(config)

    when:
    accountService.getProviderAccount(DEPLOYMENT, PROVIDER, ACCOUNT_NAME)

    then:
    HalException ex = thrown()
    ex.problems.problems[0].message.contains("More than one account")
  }

  def "fail to load a duplicate account node in multiple providers"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: 
    $PROVIDER:
      enabled: true
      accounts:
        - name: $ACCOUNT_NAME
    $PROVIDER2:
      enabled: true
      accounts:
        - name: $ACCOUNT_NAME
"""
    def accountService = makeAccountService(config)

    when:
    accountService.getAnyProviderAccount(DEPLOYMENT, ACCOUNT_NAME)

    then:
    HalException ex = thrown()
    ex.problems.problems[0].message.contains("More than one account")
  }

  def "fail to load an account in the wrong provider"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: 
    $PROVIDER2:
      enabled: true
      accounts:
        - name: $ACCOUNT_NAME
"""
    def accountService = makeAccountService(config)

    when:
    accountService.getProviderAccount(DEPLOYMENT, PROVIDER, ACCOUNT_NAME)

    then:
    HalException ex = thrown()
    ex.problems.problems[0].message.contains("No account with name")
  }

  def "load an account without provider specified"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: 
    $PROVIDER2:
      enabled: true
      accounts:
        - name: $ACCOUNT_NAME
"""
    def accountService = makeAccountService(config)

    when:
    def result = accountService.getAnyProviderAccount(DEPLOYMENT, ACCOUNT_NAME)

    then:
    result != null
  }
}
