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

import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.DefaultAllowedAccountsValidator
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Unroll

class DefaultAllowedAccountsValidatorSpec extends Specification {
  TestAccountCredentials credentialsWithRequiredGroup = new TestAccountCredentials(name: 'TestAccount', requiredGroupMembership: ['targetAccount1'])

  @Unroll
  void "should reject if allowed accounts does not intersect with required group memberships"() {
    given:
    def errors = Mock(Errors)
    def validator = new DefaultAllowedAccountsValidator(Mock(AccountCredentialsProvider) {
      1 * getAll() >> { [credentialsWithRequiredGroup] }
      0 * _
    })

    when:
    def description = new TestDescription(credentials: credentialsWithRequiredGroup)

    validator.validate("TestUser", allowedAccounts, description, errors)

    then:
    rejectValueCount * errors.rejectValue("credentials", "unauthorized", _)

    where:
    allowedAccounts                      || rejectValueCount
    []                                   || 1
    null                                 || 1
    ["testAccount"]                      || 0
    ["testaccount"]                      || 0
    ["TestAccount"]                      || 0
  }

  void "should allow if allow accounts intersect with required group memberships"() {
    given:
    def errors = Mock(Errors)
    def validator = new DefaultAllowedAccountsValidator(Mock(AccountCredentialsProvider) {
      1 * getAll() >> { [credentialsWithRequiredGroup] }
      0 * _
    })

    when:
    def description = new TestDescription(credentials: credentialsWithRequiredGroup)

    validator.validate("TestUser", ["TestAccount", "RandomAccount"], description, errors)

    then:
    0 * errors.rejectValue(_, _, _)
  }

  void "should allow if no required group memberships"() {
    given:
    def errors = Mock(Errors)
    def validator = new DefaultAllowedAccountsValidator(Mock(AccountCredentialsProvider))

    when:
    validator.validate("TestAccount", [], new TestDescription(), errors)

    then:
    0 * errors.rejectValue(_, _, _)
  }

  static class TestAccountCredentials implements AccountCredentials<TestCredentials> {
    String name
    String environment
    String accountType
    TestCredentials credentials
    String cloudProvider
    List<String> requiredGroupMembership
  }

  static class TestDescription {
    TestAccountCredentials credentials
  }

  static class TestCredentials {}
}
