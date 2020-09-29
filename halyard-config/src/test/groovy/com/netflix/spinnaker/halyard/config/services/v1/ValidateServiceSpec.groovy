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
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder
import com.netflix.spinnaker.halyard.config.validate.v1.ValidatorCollection
import spock.lang.Specification

class ValidateServiceSpec extends Specification {
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

  def "tracking validator hits all accounts"() {
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
    def validateService = new ValidateService()
    validateService.parser = mocker.mockHalconfigParser(config)
    def filter = new NodeFilter()
        .setDeployment(DEPLOYMENT_NAME)
        .withAnyProvider()
        .withAnyAccount()
    def validator = new TrackingAccountValidator()
    validateService.validatorCollection = new ValidatorCollection()
    validateService.validatorCollection.validators = [validator]

    when:
    validateService.validateMatchingFilter(filter)

    then:
    validator.validatedAccounts.size() == 4
    validator.validatedAccounts.contains(KUBERNETES_ACCOUNT_NAME)
    validator.validatedAccounts.contains(DOCKER_REGISTRY_ACCOUNT_NAME)
    validator.validatedAccounts.contains(GOOGLE_ACCOUNT_NAME)
    validator.validatedAccounts.contains(AZURE_ACCOUNT_NAME)
  }

  class TrackingAccountValidator extends Validator<Account> {
    List<String> validatedAccounts = []

    @Override
    void validate(ConfigProblemSetBuilder p, Account n) {
      validatedAccounts.add(n.getName())
    }
  }
}
