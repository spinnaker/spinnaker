/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.validators

import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class StandardKubernetesAttributeValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final DECORATOR = "decorator"
  private static final List<String> NAMESPACES = ["default", "prod"]
  private static final List<LinkedDockerRegistryConfiguration> DOCKER_REGISTRY_ACCOUNTS = [
    new LinkedDockerRegistryConfiguration(accountName: "my-docker-account"),
    new LinkedDockerRegistryConfiguration(accountName: "restricted-docker-account", namespaces: ["prod"])]

  @Shared
  KubernetesV1Credentials credentials

  @Shared
  DefaultAccountCredentialsProvider accountCredentialsProvider

  void setupSpec() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def apiMock = Mock(KubernetesApiAdaptor)
    def accountCredentialsRepositoryMock = Mock(AccountCredentialsRepository)

    DOCKER_REGISTRY_ACCOUNTS.forEach({ account ->
      def dockerRegistryAccountMock = Mock(DockerRegistryNamedAccountCredentials)
      accountCredentialsRepositoryMock.getOne(account.accountName) >> dockerRegistryAccountMock
      dockerRegistryAccountMock.getAccountName() >> account
      NAMESPACES.forEach({ namespace ->
        apiMock.getSecret(namespace, account.accountName) >> null
        apiMock.createSecret(namespace, _) >> null
      })
    })

    credentials = new KubernetesV1Credentials(apiMock, NAMESPACES, [], DOCKER_REGISTRY_ACCOUNTS, accountCredentialsRepositoryMock)
    def namedAccountCredentials =Mock(KubernetesNamedAccountCredentials) {
      getName() >> ACCOUNT_NAME
      getCredentials() >> credentials
    }
    credentialsRepo.save(ACCOUNT_NAME, namedAccountCredentials)
  }

  void "notEmpty accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateNotEmpty("not-empty", label)
    then:
      0 * errorsMock._

    when:
      validator.validateNotEmpty(" ", label)
    then:
      0 * errorsMock._

    when:
      validator.validateNotEmpty([[]], label)
    then:
      0 * errorsMock._

    when:
      validator.validateNotEmpty([null], label)
    then:
      0 * errorsMock._

    when:
      validator.validateNotEmpty(0, label)
    then:
      0 * errorsMock._
  }

  @Unroll
  void "notEmpty reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateNotEmpty(null, label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
      0 * errorsMock._

    when:
      validator.validateNotEmpty("", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
      0 * errorsMock._

    when:
      validator.validateNotEmpty([], label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
      0 * errorsMock._
  }

  void "nonNegative accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateNonNegative(0, label)
    then:
      0 * errorsMock._

    when:
      validator.validateNonNegative(1, label)
    then:
      0 * errorsMock._

    when:
      validator.validateNonNegative(1 << 30, label)
    then:
      0 * errorsMock._
  }

  void "nonNegative reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateNonNegative(-1, label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.negative")
      0 * errorsMock._
  }

  void "byRegex accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"
      def pattern = /^[a-z0-9A-Z_-]{2,10}$/

    when:
      validator.validateByRegex("check-me", label, pattern)
    then:
      0 * errorsMock._

    when:
      validator.validateByRegex("1-2_3-f", label, pattern)
    then:
      0 * errorsMock._
  }

  void "byRegex reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"
      def pattern = /^[a-z0-9A-Z_-]{2,10}$/

    when:
      validator.validateByRegex("too-big-to-fail", label, pattern)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${pattern})")
      0 * errorsMock._

    when:
      validator.validateByRegex("1", label, pattern)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${pattern})")
      0 * errorsMock._

    when:
      validator.validateByRegex("a space", label, pattern)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${pattern})")
      0 * errorsMock._
  }

  void "credentials reject (empty)"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)

    when:
      validator.validateCredentials(null, accountCredentialsProvider)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.account", "${DECORATOR}.account.empty")
      0 * errorsMock._

    when:
      validator.validateCredentials("", accountCredentialsProvider)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.account", "${DECORATOR}.account.empty")
      0 * errorsMock._
  }

  void "credentials reject (unknown)"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)

    when:
      validator.validateCredentials("You-don't-know-me", accountCredentialsProvider)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.account", "${DECORATOR}.account.notFound")
      0 * errorsMock._
  }

  void "credentials accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)

    when:
      validator.validateCredentials(ACCOUNT_NAME, accountCredentialsProvider)
    then:
      0 * errorsMock._
  }

  void "details accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateDetails("valid", label)
    then:
      0 * errorsMock._

    when:
      validator.validateDetails("also-valid", label)
    then:
      0 * errorsMock._

    when:
      validator.validateDetails("123-456-789", label)
    then:
      0 * errorsMock._

    when:
      validator.validateDetails("", label)
    then:
      0 * errorsMock._
  }

  void "details reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateDetails("-", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      0 * errorsMock._

    when:
      validator.validateDetails("a space", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      0 * errorsMock._

    when:
      validator.validateDetails("bad*details", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      0 * errorsMock._

    when:
      validator.validateDetails("-k-e-b-a-b-", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      0 * errorsMock._
  }

  void "name accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateName("valid", label)
    then:
      0 * errorsMock._

    when:
      validator.validateName("mega-valid-name", label)
    then:
      0 * errorsMock._

    when:
      validator.validateName("call-me-123-456-7890", label)
    then:
      0 * errorsMock._
  }

  void "name reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateName("-", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      0 * errorsMock._

    when:
      validator.validateName("an_underscore", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      0 * errorsMock._

    when:
      validator.validateName("?name", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      0 * errorsMock._

    when:
      validator.validateName("", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
      0 * errorsMock._
  }

  void "secretName accept"() {
    setup:
    def errorsMock = Mock(Errors)
    def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
    def label = "label"

    when:
    validator.validateSecretName("valid", label)
    then:
    0 * errorsMock._

    when:
    validator.validateSecretName("mega-valid-name", label)
    then:
    0 * errorsMock._

    when:
    validator.validateSecretName("call-me-123-456-7890", label)
    then:
    0 * errorsMock._

    when:
    validator.validateSecretName("dots.are.valid-too", label)
    then:
    0 * errorsMock._
  }

  void "secretName reject"() {
    setup:
    def errorsMock = Mock(Errors)
    def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
    def label = "label"

    when:
    validator.validateSecretName("-", label)
    then:
    1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.dnsSubdomainPattern})")
    0 * errorsMock._

    when:
    validator.validateSecretName("an_underscore", label)
    then:
    1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.dnsSubdomainPattern})")
    0 * errorsMock._

    when:
    validator.validateSecretName("?name", label)
    then:
    1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.dnsSubdomainPattern})")
    0 * errorsMock._

    when:
    validator.validateSecretName("", label)
    then:
    1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
    0 * errorsMock._
  }


  void "application accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateApplication("valid", label)
    then:
      0 * errorsMock._

    when:
      validator.validateApplication("application", label)
    then:
      0 * errorsMock._

    when:
      validator.validateApplication("7890", label)
    then:
      0 * errorsMock._
  }

  void "application reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateApplication("l-l", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.prefixPattern})")
      0 * errorsMock._

    when:
      validator.validateApplication("?application", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.prefixPattern})")
      0 * errorsMock._

    when:
      validator.validateApplication("", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
      0 * errorsMock._
  }

  void "stack accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateStack("valid", label)
    then:
      0 * errorsMock._

    when:
      validator.validateStack("stack", label)
    then:
      0 * errorsMock._

    when:
      validator.validateStack("7890", label)
    then:
      0 * errorsMock._
  }

  void "stack reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateStack("l-l", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.prefixPattern})")
      0 * errorsMock._

    when:
      validator.validateStack("?stack", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.prefixPattern})")
      0 * errorsMock._
  }

  void "memory accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateMemory("", label)
    then:
      0 * errorsMock._

    when:
      validator.validateMemory("100Mi", label)
    then:
      0 * errorsMock._

    when:
      validator.validateMemory("1Gi", label)
    then:
      0 * errorsMock._
  }

  void "memory reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateMemory("   100", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.quantityPattern})")
      0 * errorsMock._

    when:
      validator.validateMemory("x100Gi", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.quantityPattern})")
      0 * errorsMock._

    when:
      validator.validateMemory("1Tt!i", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.quantityPattern})")
      0 * errorsMock._
  }

  void "cpu accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateCpu("", label)
    then:
      0 * errorsMock._

    when:
      validator.validateCpu("100m", label)
    then:
      0 * errorsMock._

    when:
      validator.validateCpu("2m", label)
    then:
      0 * errorsMock._
  }

  void "cpu reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateCpu("100z", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.quantityPattern})")
      0 * errorsMock._

    when:
      validator.validateCpu("?", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.quantityPattern})")
      0 * errorsMock._

    when:
      validator.validateCpu("- ", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.quantityPattern})")
      0 * errorsMock._
  }

  void "namespace accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateNamespace(credentials, "", label)
    then:
      0 * errorsMock._

    when:
      validator.validateNamespace(credentials, NAMESPACES[0], label)
    then:
      0 * errorsMock._

    when:
      validator.validateNamespace(credentials, NAMESPACES[1], label)
    then:
      0 * errorsMock._
  }

  void "namespace reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateNamespace(credentials, " .-100z", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.notRegistered")
      0 * errorsMock._

    when:
      validator.validateNamespace(credentials, "?", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.notRegistered")
      0 * errorsMock._

    when:
      validator.validateNamespace(credentials, "- ", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.notRegistered")
      0 * errorsMock._
  }

  void "image pull secret accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateImagePullSecret(credentials, DOCKER_REGISTRY_ACCOUNTS[0].accountName, NAMESPACES[0], label)
    then:
      0 * errorsMock._

    when:
      validator.validateImagePullSecret(credentials, DOCKER_REGISTRY_ACCOUNTS[1].accountName, NAMESPACES[1], label)
    then:
      0 * errorsMock._
  }

  void "image pull secret reject"() {
    setup:
    def errorsMock = Mock(Errors)
    def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
    def label = "label"

    when:
      validator.validateImagePullSecret(credentials, DOCKER_REGISTRY_ACCOUNTS[1].accountName, NAMESPACES[0], label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.notRegistered")
      0 * errorsMock._

    when:
      validator.validateImagePullSecret(credentials, "?", NAMESPACES[0], label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.notRegistered")
      0 * errorsMock._

    when:
      validator.validateImagePullSecret(credentials, DOCKER_REGISTRY_ACCOUNTS[0].accountName, "not a namespace", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.notRegistered")
      0 * errorsMock._
  }

  void "port accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validatePort(80, label)
    then:
      0 * errorsMock._

    when:
      validator.validatePort(111, label)
    then:
      0 * errorsMock._

    when:
      validator.validatePort(65535, label)
    then:
      0 * errorsMock._
  }

  void "port reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validatePort(0, label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must be in range [1, $StandardKubernetesAttributeValidator.maxPort])")
      0 * errorsMock._

    when:
      validator.validatePort(-1, label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must be in range [1, $StandardKubernetesAttributeValidator.maxPort])")
      0 * errorsMock._

    when:
      validator.validatePort(65536, label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must be in range [1, $StandardKubernetesAttributeValidator.maxPort])")
      0 * errorsMock._
  }

  void "protocol accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateProtocol('TCP', label)
    then:
      0 * errorsMock._

    when:
      validator.validateProtocol('UDP', label)
    then:
      0 * errorsMock._
  }

  void "protocol reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateProtocol('', label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
      0 * errorsMock._

    when:
      validator.validateProtocol('UPD', label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must be one of $StandardKubernetesAttributeValidator.protocolList)")
      0 * errorsMock._
  }

  void "http path accept"() {
    setup:
    def errorsMock = Mock(Errors)
    def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
    def label = "label"

    when:
      validator.validateHttpPath('/', label)
    then:
      0 * errorsMock._

    when:
      validator.validateHttpPath('/path-to/segment\\ 3/4', label)
    then:
      0 * errorsMock._

  }

  void "http path reject"() {
    setup:
    def errorsMock = Mock(Errors)
    def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
    def label = "label"

    when:
      validator.validateHttpPath('', label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
      0 * errorsMock._

    when:
      validator.validateHttpPath('path-to/segment\\ 3/4', label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.httpPathPattern})")
      0 * errorsMock._
  }


  void "path accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validatePath('/', label)
    then:
      0 * errorsMock._

    when:
      validator.validatePath('/path-to/dir12\\ 3/4', label)
    then:
      0 * errorsMock._

    when:
      validator.validatePath('C:\\\\', label)
    then:
      0 * errorsMock._

    when:
      validator.validatePath('C:\\\\mount\\\\ dir', label)
    then:
      0 * errorsMock._

    when:
      validator.validatePath('C:/', label)
    then:
      0 * errorsMock._

    when:
      validator.validatePath('C:/mount/ dir', label)
    then:
      0 * errorsMock._
  }

  void "path reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validatePath('', label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
      0 * errorsMock._

    when:
      validator.validatePath('path-to/dir12\\ 3/4', label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.unixPathPattern} or ${StandardKubernetesAttributeValidator.winPathPattern})")
      0 * errorsMock._

    when:
      validator.validatePath('C:', label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardKubernetesAttributeValidator.unixPathPattern} or ${StandardKubernetesAttributeValidator.winPathPattern})")
      0 * errorsMock._
  }

  void "relative path accept"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateRelativePath('path-to/dir12\\\\ 3/4', label)
    then:
      0 * errorsMock._

  }

  void "relative path reject"() {
    setup:
      def errorsMock = Mock(Errors)
      def validator = new StandardKubernetesAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateRelativePath('', label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
      0 * errorsMock._

    when:
      validator.validateRelativePath('/path-to/dir12\\ 3/4', label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must not match ${StandardKubernetesAttributeValidator.unixPathPattern} or ${StandardKubernetesAttributeValidator.winPathPattern})")
      0 * errorsMock._

    when:
      validator.validateRelativePath('C:\\\\mount\\\\ dir', label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must not match ${StandardKubernetesAttributeValidator.unixPathPattern} or ${StandardKubernetesAttributeValidator.winPathPattern})")
      0 * errorsMock._

    when:
      validator.validateRelativePath('C:/mount/ dir', label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must not match ${StandardKubernetesAttributeValidator.unixPathPattern} or ${StandardKubernetesAttributeValidator.winPathPattern})")
      0 * errorsMock._
  }
}
