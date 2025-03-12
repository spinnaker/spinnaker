/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.validators

import com.netflix.spinnaker.clouddriver.appengine.gitClient.AppengineGitCredentialType
import com.netflix.spinnaker.clouddriver.appengine.gitClient.AppengineGitCredentials
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.model.ShardBy
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class StandardAppengineAttributeValidatorSpec extends Specification {
  private static final DECORATOR = "decorator"
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final APPLICATION_NAME = "test-app"
  private static final REGION = "us-central"
  private static final DEFAULT_LOAD_BALANCER_NAME = "default"
  private static final BACKEND_LOAD_BALANCER_NAME = "backend"
  private static final LATENCY_LOAD_BALANCER_NAME = "latency_sensitive"


  private static final SERVER_GROUP_NAME_1 = "app-stack-detail-v000"
  private static final SERVER_GROUP_1 = new AppengineServerGroup(
    name: SERVER_GROUP_NAME_1,
    loadBalancers: [DEFAULT_LOAD_BALANCER_NAME]
  )

  private static final SERVER_GROUP_NAME_2 = "app-stack-detail-v001"
  private static final SERVER_GROUP_2 = new AppengineServerGroup(
    name: SERVER_GROUP_NAME_2,
    loadBalancers: [DEFAULT_LOAD_BALANCER_NAME, BACKEND_LOAD_BALANCER_NAME, LATENCY_LOAD_BALANCER_NAME]
  )

  private static final SERVER_GROUP_NAME_3 = "allows-gradual-migration"
  private static final SERVER_GROUP_3 = new AppengineServerGroup(
    name: SERVER_GROUP_NAME_3,
    loadBalancers: [DEFAULT_LOAD_BALANCER_NAME],
    allowsGradualTrafficMigration: true
  )

  @Shared
  CredentialsRepository<AppengineNamedAccountCredentials> credentialsRepository

  @Shared
  AppengineGitCredentials gitCredentials

  @Shared
  AppengineNamedAccountCredentials namedAccountCredentials

  void setupSpec() {
    credentialsRepository = new MapBackedCredentialsRepository<>(AppengineNamedAccountCredentials.CREDENTIALS_TYPE,
      new NoopCredentialsLifecycleHandler<>())

    def mockCredentials = Mock(AppengineCredentials)
    namedAccountCredentials = new AppengineNamedAccountCredentials.Builder()
      .name(ACCOUNT_NAME)
      .region(REGION)
      .applicationName(APPLICATION_NAME)
      .credentials(mockCredentials)
      .build()

    credentialsRepository.save(namedAccountCredentials)

    gitCredentials = new AppengineGitCredentials(
      httpsUsernamePasswordCredentialsProvider: Mock(UsernamePasswordCredentialsProvider)
    )
  }

  void "validate non-empty valid"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errors)
      def label = "attribute"

    when:
      validator.validateNotEmpty("something", label)
    then:
      0 * errors._

    when:
      validator.validateNotEmpty(["something"], label)
    then:
      0 * errors._

    when:
      validator.validateNotEmpty([""], label)
    then:
      0 * errors._

    when:
      validator.validateNotEmpty([null], label)
    then:
      0 * errors._
  }

  void "validate non-empty invalid"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errors)
      def label = "attribute"

    when:
      validator.validateNotEmpty(null, label)
    then:
      1 * errors.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")

    when:
      validator.validateNotEmpty("", label)
    then:
      1 * errors.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")

    when:
      validator.validateNotEmpty([], label)
    then:
      1 * errors.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
  }

  void "validate by regex valid"() {
    setup:
      def errors = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errors)
      def label = "attribute"

    when:
      validator.validateByRegex("app-engine", label, /\w{3}-\w{6}/)
    then:
      0 * errors._
  }

  void "validate by regex invalid"() {
    setup:
    def errors = Mock(ValidationErrors)
    def validator = new StandardAppengineAttributeValidator(DECORATOR, errors)
    def label = "attribute"
    def regex = /\w{3}-\w{6}/

    when:
      validator.validateByRegex("app-engine-flex", label, regex)
    then:
      1 * errors.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${regex})")
  }

  void "credentials reject (empty)"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)

    when:
      validator.validateCredentials(null, credentialsRepository)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.account", "${DECORATOR}.account.empty")
      0 * errorsMock._

    when:
      validator.validateCredentials("", credentialsRepository)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.account", "${DECORATOR}.account.empty")
      0 * errorsMock._
  }

  void "credentials reject (unknown)"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)

    when:
      validator.validateCredentials("You-don't-know-me", credentialsRepository)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.account", "${DECORATOR}.account.notFound")
      0 * errorsMock._
  }

  void "credentials accept"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)

    when:
      validator.validateCredentials(ACCOUNT_NAME, credentialsRepository)
    then:
      0 * errorsMock._
  }

  void "git credentials reject"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)

    when:
      validator.validateGitCredentials(gitCredentials, AppengineGitCredentialType.SSH, ACCOUNT_NAME, "gitCredentialType")

    then:
      1 * errorsMock.rejectValue("decorator.gitCredentialType",
                                 "decorator.gitCredentialType.invalid " +
                                 "(Account my-appengine-account supports only the following git credential types: NONE, HTTPS_USERNAME_PASSWORD")
  }

  void "git credentials reject (empty)"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)

    when:
      validator.validateGitCredentials(gitCredentials, null, ACCOUNT_NAME, "gitCredentialType")

    then:
      1 * errorsMock.rejectValue("decorator.gitCredentialType", "decorator.gitCredentialType.empty")
  }

  void "git credentials accept"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)

    when:
      validator.validateGitCredentials(gitCredentials, AppengineGitCredentialType.HTTPS_USERNAME_PASSWORD, ACCOUNT_NAME, "gitCredentialType")

    then:
      0 * errorsMock._
  }

  void "details accept"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
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
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateDetails("-", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardAppengineAttributeValidator.namePattern})")
      0 * errorsMock._

    when:
      validator.validateDetails("a space", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardAppengineAttributeValidator.namePattern})")
      0 * errorsMock._

    when:
      validator.validateDetails("bad*details", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardAppengineAttributeValidator.namePattern})")
      0 * errorsMock._

    when:
      validator.validateDetails("-k-e-b-a-b-", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardAppengineAttributeValidator.namePattern})")
      0 * errorsMock._
  }

  void "application accept"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
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
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateApplication("l-l", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardAppengineAttributeValidator.prefixPattern})")
      0 * errorsMock._

    when:
      validator.validateApplication("?application", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardAppengineAttributeValidator.prefixPattern})")
      0 * errorsMock._

    when:
      validator.validateApplication("", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.empty")
      0 * errorsMock._
  }

  void "stack accept"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
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
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
      def label = "label"

    when:
      validator.validateStack("l-l", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardAppengineAttributeValidator.prefixPattern})")
      0 * errorsMock._

    when:
      validator.validateStack("?stack", label)
    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Must match ${StandardAppengineAttributeValidator.prefixPattern})")
      0 * errorsMock._
  }

  @Unroll
  void "allocations accept"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
      def label = "allocations"

    when:
      validator.validateAllocations(allocation, shardBy, label)

    then:
      0 * errorsMock._

    where:
      allocation                         | shardBy
      [a: 0.7, b: 0.11, c: 0.09, d: 0.1] | ShardBy.IP
      [a: 0.888, b: 0.112]               | ShardBy.COOKIE
  }

  @Unroll
  void "allocations reject (wrong number of decimal places)"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
      def label = "allocations"

    when:
      validator.validateAllocations(allocation, shardBy, label)

    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid " + errorMessage)

    where:
      allocation                        | shardBy        || errorMessage
      [a: 0.888, b: 0.112]              | ShardBy.IP     || "(Allocations invalid for a, b. Allocations for shard type IP can have up to 2 decimal places.)"
      [a: 0.8888, b: 0.1111, c: 0.0001] | ShardBy.COOKIE || "(Allocations invalid for a, b, c. Allocations for shard type COOKIE can have up to 3 decimal places.)"
  }

  void "allocations reject (does not sum to 1)"() {
    setup:
      def errorsMock = Mock(ValidationErrors)
      def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
      def label = "allocations"

    when:
      validator.validateAllocations([a: 0.75], ShardBy.COOKIE, label)

    then:
      1 * errorsMock.rejectValue("${DECORATOR}.${label}", "${DECORATOR}.${label}.invalid (Allocations must sum to 1)")
}

void "serverGroup reject not found"() {
  setup:
  def errorsMock = Mock(ValidationErrors)
  def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
  def label = "allocations"
  def serverGroupName = "not_exists"
  def mockCluster = Mock(AppengineClusterProvider)

  when:
  validator.validateServerGroupsCanBeEnabled([serverGroupName], DEFAULT_LOAD_BALANCER_NAME, namedAccountCredentials, mockCluster, "split.allocations")

  then:
  1 * errorsMock.rejectValue("${DECORATOR}.split.${label}", "${DECORATOR}.split.${label}.invalid (Server group ${serverGroupName} not found).")
}

  void "serverGroup valid"() {
    setup:
    def errorsMock = Mock(ValidationErrors)
    def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
    def label = "allocations"
    def mockCluster = Mock(AppengineClusterProvider)
    mockCluster.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_1) >> SERVER_GROUP_1
    mockCluster.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_2) >> SERVER_GROUP_2
    mockCluster.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_3) >> SERVER_GROUP_3

    when:
    validator.validateServerGroupsCanBeEnabled([SERVER_GROUP_NAME_1], DEFAULT_LOAD_BALANCER_NAME, namedAccountCredentials, mockCluster, "split.allocations")

    then:
    0 * errorsMock._
  }

  void "same name serverGroup valid"() {
    setup:
    def errorsMock = Mock(ValidationErrors)
    def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
    def label = "allocations"
    def mockCluster = Mock(AppengineClusterProvider)
    mockCluster.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_1) >> SERVER_GROUP_1
    mockCluster.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_2) >> SERVER_GROUP_2
    mockCluster.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_3) >> SERVER_GROUP_3

    when:
    validator.validateServerGroupsCanBeEnabled([SERVER_GROUP_NAME_2], DEFAULT_LOAD_BALANCER_NAME, namedAccountCredentials, mockCluster, "split.allocations")
    validator.validateServerGroupsCanBeEnabled([SERVER_GROUP_NAME_2], BACKEND_LOAD_BALANCER_NAME, namedAccountCredentials, mockCluster, "split.allocations")
    validator.validateServerGroupsCanBeEnabled([SERVER_GROUP_NAME_2], LATENCY_LOAD_BALANCER_NAME, namedAccountCredentials, mockCluster, "split.allocations")

    then:
    0 * errorsMock._
  }

void "serverGroup reject not registered with load balancer"() {
  setup:
  def errorsMock = Mock(ValidationErrors)
  def validator = new StandardAppengineAttributeValidator(DECORATOR, errorsMock)
  def label = "allocations"
  def loadBalancer = "not_exists_loadBalancer"
  def mockCluster = Mock(AppengineClusterProvider)
  mockCluster.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_1) >> SERVER_GROUP_1
  mockCluster.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_2) >> SERVER_GROUP_2
  mockCluster.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME_3) >> SERVER_GROUP_3

  when:
  validator.validateServerGroupsCanBeEnabled([SERVER_GROUP_NAME_2], loadBalancer, namedAccountCredentials, mockCluster, "split.allocations")

  then:
  1 * errorsMock.rejectValue("${DECORATOR}.split.${label}", "${DECORATOR}.split.${label}.invalid (Server group ${SERVER_GROUP_NAME_2} not registered with load balancer ${loadBalancer}).")
}
}
