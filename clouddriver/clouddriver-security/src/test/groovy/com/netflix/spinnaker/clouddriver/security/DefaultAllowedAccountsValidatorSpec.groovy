/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.security

import com.netflix.spinnaker.clouddriver.security.resources.NonCredentialed
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.fiat.shared.FiatStatus
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DefaultAllowedAccountsValidatorSpec extends Specification {

  @Shared
  TestAccountCredentials credentialsWithRequiredGroup = new TestAccountCredentials(
      name: 'TestAccount',
      requiredGroupMembership: ['targetAccount1']
  )

  @Shared
  TestAccountCredentials credentialsWithSameRequiredGroup = new TestAccountCredentials(
      name: 'OtherAccount',
      requiredGroupMembership: ['targetAccount1']
  )

  @Shared
  TestAccountCredentials credentialsWithDifferentRequiredGroup = new TestAccountCredentials(
      name: 'AnotherAccount',
      requiredGroupMembership: ['targetAccount2']
  )

  @Shared
  TestAccountCredentials credentialWithPermissions = new TestAccountCredentials(
      name: 'TestAccount',
      permissions: new Permissions.Builder().add(Authorization.WRITE, 'targetAccount1').build()
  )

  @Shared
  TestAccountCredentials credentialsWithConflictingPermsRGM = new TestAccountCredentials(
      name: 'TestAccount',
      requiredGroupMembership: ['targetAccount2'],
      permissions: new Permissions.Builder().add(Authorization.WRITE, 'targetAccount1').build()
  )

  def accountCredentialsProvider = Mock(AccountCredentialsProvider)
  def fiatStatus = Mock(FiatStatus) {
    _ * isEnabled() >> { return false }
  }

  @Subject
  def validator = new DefaultAllowedAccountsValidator(accountCredentialsProvider, fiatStatus)

  @Unroll
  void "should reject if allowed accounts does not intersect with required group memberships"() {
    given:
    def errors = Mock(Errors)
    def description = new TestDescription(credentials: credentialsWithRequiredGroup)

    when:
    validator.validate("TestUser", allowedAccounts, description, errors)

    then:
    1 * accountCredentialsProvider.getAll() >> { [credentialsWithRequiredGroup] }
    0 * accountCredentialsProvider._

    rejectValueCount * errors.rejectValue("credentials", "unauthorized", _)

    where:
    allowedAccounts || rejectValueCount
    []              || 1
    null            || 1
    ["testAccount"] || 0
    ["testaccount"] || 0
    ["TestAccount"] || 0
  }

  void "should allow if allow accounts intersect with required group memberships"() {
    given:
    def errors = Mock(Errors)
    def description = new TestDescription(credentials: credentialsWithRequiredGroup)

    when:
    validator.validate("TestUser", ["TestAccount", "RandomAccount"], description, errors)

    then:
    1 * accountCredentialsProvider.getAll() >> { [credentialsWithRequiredGroup] }
    0 * accountCredentialsProvider._
    0 * errors.rejectValue(_, _, _)
  }

  @Unroll
  void "should allow if all accounts intersection with required group memberships"() {
    given:
    def errors = Mock(Errors)
    def description = new MultiAccountDescription(credentials: requiredCredentials)

    when:
    validator.validate("TestUser", userAccounts, description, errors)

    then:
    1 * accountCredentialsProvider.getAll() >> { [credentialsWithRequiredGroup] }
    0 * accountCredentialsProvider._
    rejectValueCount * errors.rejectValue(_, _, _)

    where:
    requiredCredentials                                                                                                             | userAccounts                     || rejectValueCount
    [credentialsWithRequiredGroup, credentialsWithSameRequiredGroup, credentialWithPermissions, credentialsWithConflictingPermsRGM] | ["TestAccount", "OtherAccount"]  || 0
    [credentialsWithRequiredGroup, credentialsWithDifferentRequiredGroup]                                                           | ["TestAccount", "OtherAccount"]  || 1
    [credentialsWithRequiredGroup, credentialsWithDifferentRequiredGroup]                                                           | ["ProdAccount", "RandomAccount"] || 2
  }

  void "should allow if no required group memberships"() {
    given:
    def errors = Mock(Errors)

    when:
    validator.validate("TestAccount", [], new TestDescription(), errors)

    then:
    0 * errors.rejectValue(_, _, _)
  }

  void "should allow if description is non-credentialed"() {
    given:
    def errors = Mock(Errors)

    when:
    validator.validate("TestAccount", [], new TestGlobalDescription(), errors)

    then:
    1 * accountCredentialsProvider.getAll() >> { [testCredential] }
    0 * accountCredentialsProvider._
    0 * errors.rejectValue(_, _, _)

    where:
    testCredential << [credentialsWithRequiredGroup, credentialWithPermissions]
  }

  void "should reject if no credentials in description"() {
    given:
    def errors = Mock(Errors)

    when:
    validator.validate("TestAccount", [], new InvalidDescription(), errors)

    then:
    1 * accountCredentialsProvider.getAll() >> { [credentialsWithRequiredGroup] }
    0 * accountCredentialsProvider._
    1 * errors.rejectValue("credentials", "missing", _)
  }

  void "should short circuit if fiat is enabled"() {
    given:
    def errors = Mock(Errors)

    when:
    validator.validate("TestAccount", [], new InvalidDescription(), errors)

    then:
    1 * fiatStatus.isEnabled() >> { return true }
    0 * _
  }

  static class TestAccountCredentials implements AccountCredentials<TestCredentials> {
    String name
    String environment
    String accountType
    TestCredentials credentials
    String cloudProvider
    List<String> requiredGroupMembership
    Permissions permissions
  }

  static class TestDescription {
    TestAccountCredentials credentials
  }

  static class TestGlobalDescription implements NonCredentialed {

  }

  static class MultiAccountDescription {
    Set<TestAccountCredentials> credentials = []
  }

  static class InvalidDescription {}

  static class TestCredentials {}
}
