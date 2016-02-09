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


package com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators

import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.KubernetesResourceDescription
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class DeployKubernetesAtomicOperationValidatorSpec extends Specification {
  private static final DESCRIPTION = "deployKubernetesAtomicOperationDescription"
  private static final List<String> NAMESPACES = ["default", "prod"]
  private static final List<LinkedDockerRegistryConfiguration> DOCKER_REGISTRY_ACCOUNTS = [
    new LinkedDockerRegistryConfiguration(accountName: "my-docker-account"),
    new LinkedDockerRegistryConfiguration(accountName: "restricted-docker-account", namespaces: ["prod"])]

  private static final VALID_APPLICATION = "app"
  private static final VALID_STACK = "stack"
  private static final VALID_DETAILS = "the-details"
  private static final VALID_TARGET_SIZE = 3
  private static final VALID_IMAGE = "container-image"
  private static final VALID_NAME = "a-name"
  private static final VALID_MEMORY1 = "200"
  private static final VALID_MEMORY2 = "200Mi"
  private static final VALID_CPU1 = "200"
  private static final VALID_CPU2 = "200m"
  private static final VALID_CREDENTIALS = "auto"
  private static final VALID_LOAD_BALANCERS = ["x", "y"]
  private static final VALID_SECURITY_GROUPS = ["a-1", "b-2"]
  private static final VALID_NAMESPACE = NAMESPACES[0]
  private static final VALID_SECRET = DOCKER_REGISTRY_ACCOUNTS[0].accountName

  private static final INVALID_APPLICATION = "-app-"
  private static final INVALID_STACK = " stack"
  private static final INVALID_DETAILS = "the details"
  private static final INVALID_TARGET_SIZE = -7
  private static final INVALID_IMAGE = ""
  private static final INVALID_NAME = "a?name"
  private static final INVALID_MEMORY = "200?"
  private static final INVALID_CPU = "9z"
  private static final INVALID_CREDENTIALS = "valid"
  private static final INVALID_LOAD_BALANCERS = [" ", "--"]
  private static final INVALID_SECURITY_GROUPS = [" ", "--"]
  private static final INVALID_NAMESPACE = "!default"

  @Shared
  DeployKubernetesAtomicOperationValidator validator

  void setupSpec() {
    validator = new DeployKubernetesAtomicOperationValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def namedCredentialsMock = Mock(KubernetesNamedAccountCredentials)

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

    def credentials = new KubernetesCredentials(apiMock, NAMESPACES, DOCKER_REGISTRY_ACCOUNTS, accountCredentialsRepositoryMock)
    namedCredentialsMock.getName() >> VALID_CREDENTIALS
    namedCredentialsMock.getCredentials() >> credentials
    credentialsRepo.save(VALID_CREDENTIALS, namedCredentialsMock)
    validator.accountCredentialsProvider = credentialsProvider
  }

  KubernetesContainerDescription fullValidContainerDescription1
  KubernetesContainerDescription fullValidContainerDescription2
  KubernetesContainerDescription partialValidContainerDescription
  KubernetesResourceDescription fullValidResourceDescription1
  KubernetesResourceDescription fullValidResourceDescription2
  KubernetesResourceDescription partialValidResourceDescription

  KubernetesContainerDescription fullInvalidContainerDescription
  KubernetesContainerDescription partialInvalidContainerDescription
  KubernetesResourceDescription fullInvalidResourceDescription

  void setup() {
    fullValidResourceDescription1 = new KubernetesResourceDescription(memory: VALID_MEMORY1, cpu: VALID_CPU1)
    fullValidResourceDescription2 = new KubernetesResourceDescription(memory: VALID_MEMORY2, cpu: VALID_CPU2)
    partialValidResourceDescription = new KubernetesResourceDescription(memory: VALID_MEMORY1)
    fullValidContainerDescription1 = new KubernetesContainerDescription(name: VALID_NAME, image: VALID_IMAGE, limits: fullValidResourceDescription1, requests: fullValidResourceDescription1)
    fullValidContainerDescription2 = new KubernetesContainerDescription(name: VALID_NAME, image: VALID_IMAGE, limits: fullValidResourceDescription2, requests: fullValidResourceDescription2)
    partialValidContainerDescription = new KubernetesContainerDescription(name: VALID_NAME, image: VALID_IMAGE, limits: partialValidResourceDescription)

    fullInvalidResourceDescription = new KubernetesResourceDescription(memory: INVALID_MEMORY, cpu: INVALID_CPU)
    fullInvalidContainerDescription = new KubernetesContainerDescription(name: INVALID_NAME, image: INVALID_IMAGE, limits: fullInvalidResourceDescription, requests: fullInvalidResourceDescription)
    partialInvalidContainerDescription = new KubernetesContainerDescription(name: INVALID_NAME, image: INVALID_IMAGE)
  }

  void "validation accept (all fields filled)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: VALID_APPLICATION,
        stack: VALID_STACK,
        namespace: VALID_NAMESPACE,
        freeFormDetails: VALID_STACK,
        targetSize: VALID_TARGET_SIZE,
        imagePullSecrets: [VALID_SECRET],
        containers: [
          fullValidContainerDescription1,
          fullValidContainerDescription2
        ],
        loadBalancers: VALID_LOAD_BALANCERS,
        securityGroups: VALID_SECURITY_GROUPS,
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      0 * errorsMock._
  }

  void "validation accept (minimal fields filled)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: VALID_APPLICATION,
        stack: VALID_STACK,
        targetSize: VALID_TARGET_SIZE,
        containers: [
          partialValidContainerDescription
        ],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      0 * errorsMock._
  }

  void "validation reject (missing credentials)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: VALID_APPLICATION,
        stack: VALID_STACK,
        targetSize: VALID_TARGET_SIZE,
        containers: [
          partialValidContainerDescription
        ])
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("${DESCRIPTION}.credentials", "${DESCRIPTION}.credentials.empty")
      0 * errorsMock._
  }

  void "validation reject (missing stack)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: VALID_APPLICATION,
        targetSize: VALID_TARGET_SIZE,
        containers: [
          partialValidContainerDescription
        ],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("${DESCRIPTION}.stack", "${DESCRIPTION}.stack.empty")
      0 * errorsMock._
  }

  void "validation reject (missing application)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(stack: VALID_STACK,
        targetSize: VALID_TARGET_SIZE,
        containers: [
          partialValidContainerDescription
        ],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("${DESCRIPTION}.application", "${DESCRIPTION}.application.empty")
      0 * errorsMock._
  }

  void "validation reject (invalid stack)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: VALID_APPLICATION,
        stack: INVALID_STACK,
        targetSize: VALID_TARGET_SIZE,
        containers: [
          partialValidContainerDescription
        ],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("${DESCRIPTION}.stack", "${DESCRIPTION}.stack.invalid (Must match ${StandardKubernetesAttributeValidator.prefixPattern})")
      0 * errorsMock._
  }

  void "validation reject (invalid application)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: INVALID_APPLICATION,
        stack: VALID_STACK,
        targetSize: VALID_TARGET_SIZE,
        containers: [
          partialValidContainerDescription
        ],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("${DESCRIPTION}.application", "${DESCRIPTION}.application.invalid (Must match ${StandardKubernetesAttributeValidator.prefixPattern})")
      0 * errorsMock._
  }

  void "validation reject (invalid namespace)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: VALID_APPLICATION,
        stack: VALID_STACK,
        targetSize: VALID_TARGET_SIZE,
        namespace: INVALID_NAMESPACE,
        containers: [
          partialValidContainerDescription
        ],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("${DESCRIPTION}.namespace", "${DESCRIPTION}.namespace.notRegistered")
      0 * errorsMock._
  }

  void "validation reject (invalid target size)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: VALID_APPLICATION,
        stack: VALID_STACK,
        targetSize: INVALID_TARGET_SIZE,
        containers: [
          partialValidContainerDescription
        ],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("${DESCRIPTION}.targetSize", "${DESCRIPTION}.targetSize.negative")
      0 * errorsMock._
  }

  void "validation reject (invalid partial container)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: VALID_APPLICATION,
        stack: VALID_STACK,
        targetSize: VALID_TARGET_SIZE,
        containers: [
          partialInvalidContainerDescription
        ],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("${DESCRIPTION}.container[0].name", "${DESCRIPTION}.container[0].name.invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      1 * errorsMock.rejectValue("${DESCRIPTION}.container[0].image", "${DESCRIPTION}.container[0].image.empty")
      0 * errorsMock._
  }

  void "validation reject (invalid full container)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: VALID_APPLICATION,
        stack: VALID_STACK,
        targetSize: VALID_TARGET_SIZE,
        containers: [
          fullInvalidContainerDescription
        ],
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("${DESCRIPTION}.container[0].name", "${DESCRIPTION}.container[0].name.invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      1 * errorsMock.rejectValue("${DESCRIPTION}.container[0].image", "${DESCRIPTION}.container[0].image.empty")
      1 * errorsMock.rejectValue("${DESCRIPTION}.container[0].requests.memory", "${DESCRIPTION}.container[0].requests.memory.invalid (Must match ${StandardKubernetesAttributeValidator.quantityPattern})")
      1 * errorsMock.rejectValue("${DESCRIPTION}.container[0].limits.memory", "${DESCRIPTION}.container[0].limits.memory.invalid (Must match ${StandardKubernetesAttributeValidator.quantityPattern})")
      1 * errorsMock.rejectValue("${DESCRIPTION}.container[0].requests.cpu", "${DESCRIPTION}.container[0].requests.cpu.invalid (Must match ${StandardKubernetesAttributeValidator.quantityPattern})")
      1 * errorsMock.rejectValue("${DESCRIPTION}.container[0].limits.cpu", "${DESCRIPTION}.container[0].limits.cpu.invalid (Must match ${StandardKubernetesAttributeValidator.quantityPattern})")
      0 * errorsMock._
  }

  void "validation reject (invalid load balancers)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: VALID_APPLICATION,
        stack: VALID_STACK,
        targetSize: VALID_TARGET_SIZE,
        containers: [
          partialValidContainerDescription
        ],
        loadBalancers: INVALID_LOAD_BALANCERS,
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("${DESCRIPTION}.loadBalancers[0]", "${DESCRIPTION}.loadBalancers[0].invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      1 * errorsMock.rejectValue("${DESCRIPTION}.loadBalancers[1]", "${DESCRIPTION}.loadBalancers[1].invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      0 * errorsMock._
  }

  void "validation reject (invalid security groups)"() {
    setup:
      def description = new DeployKubernetesAtomicOperationDescription(application: VALID_APPLICATION,
        stack: VALID_STACK,
        targetSize: VALID_TARGET_SIZE,
        containers: [
          partialValidContainerDescription
        ],
        securityGroups: INVALID_SECURITY_GROUPS,
        credentials: VALID_CREDENTIALS)
      def errorsMock = Mock(Errors)

    when:
      validator.validate([], description, errorsMock)
    then:
      1 * errorsMock.rejectValue("${DESCRIPTION}.securityGroups[0]", "${DESCRIPTION}.securityGroups[0].invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      1 * errorsMock.rejectValue("${DESCRIPTION}.securityGroups[1]", "${DESCRIPTION}.securityGroups[1].invalid (Must match ${StandardKubernetesAttributeValidator.namePattern})")
      0 * errorsMock._
  }
}
