/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators

import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerMethod
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 */
@Unroll
class OpenstackAttributeValidatorSpec extends Specification {

  OpenstackAttributeValidator validator
  def errors
  def accountProvider

  void setup() {
    errors = Mock(Errors)
    validator = new OpenstackAttributeValidator('context', errors)
    accountProvider = Mock(AccountCredentialsProvider)
  }

  def "ValidateByRegex"() {
    when:
    boolean actual = validator.validateByRegex(value, 'test', regex)

    then:
    actual == result
    if (!result) {
      1 * errors.rejectValue('context.test', 'context.test.invalid (Must match [A-Z]+)')
    }

    where:
    value | regex       | result
    'foo' | '[A-Za-z]+' | true
    '123' | '[A-Z]+'    | false

  }

  def "ValidateByContainment"() {
    when:
    boolean actual = validator.validateByContainment(value, 'test', list)

    then:
    actual == result
    if (!result) {
      1 * errors.rejectValue('context.test', 'context.test.invalid (Must be one of [1234])')
    }

    where:
    value | list           | result
    'foo' | ['foo', 'bar'] | true
    '123' | ['1234']       | false

  }

  def "Reject"() {
    when:
    validator.reject('foo', 'reason')

    then:
    1 * errors.rejectValue('context.foo', 'context.foo.invalid (reason)')
  }

  def "ValidatePort"() {
    when:
    boolean actual = validator.validatePort(port, 'foo')

    then:
    actual == result
    if (!result) {
      1 * errors.rejectValue('context.foo', 'context.foo.invalid (Must be in range [1, 65535])')
    }

    where:
    port | result
    80   | true
    -5   | false
  }

  def "ValidateNotEmpty"() {
    when:
    boolean actual = validator.validateNotEmpty(value, 'test')

    then:
    actual == result
    if (!result) {
      1 * errors.rejectValue('context.test', 'context.test.empty')
    }

    where:
    value | result
    'foo' | true
    ''    | false
    null  | false
  }

  def "ValidateNotNull"() {
    when:
    boolean actual = validator.validateNotNull(value, 'test')

    then:
    actual == result
    if (!result) {
      1 * errors.rejectValue('context.test', _)
    }

    where:
    value                                | result
    LoadBalancerMethod.LEAST_CONNECTIONS | true
    null                                 | false
  }

  def "ValidateNonNegative"() {
    when:
    boolean actual = validator.validateNonNegative(value, 'test')

    then:
    actual == result
    if (!result) {
      1 * errors.rejectValue('context.test', 'context.test.negative')
    }

    where:
    value | result
    1     | true
    0     | true
    -1    | false
  }

  def "ValidatePositive"() {
    when:
    boolean actual = validator.validatePositive(value, 'test')

    then:
    actual == result
    if (!result) {
      1 * errors.rejectValue('context.test', 'context.test.notPositive')
    }

    where:
    value | result
    1     | true
    0     | false
    -1    | false
  }

  def "valid account and credentials"() {
    given:
    def named = Mock(OpenstackNamedAccountCredentials)
    def cred = Mock(OpenstackCredentials)
    String account = 'account'

    when:
    boolean actual = validator.validateCredentials(account, accountProvider)

    then:
    actual
    1 * accountProvider.getCredentials(account) >> named
    1 * named.credentials >> cred
  }

  def "empty account"() {
    given:
    def named = Mock(OpenstackNamedAccountCredentials)
    def cred = Mock(OpenstackCredentials)
    String account = ''

    when:
    boolean actual = validator.validateCredentials(account, accountProvider)

    then:
    !actual
    0 * accountProvider.getCredentials(account) >> named
    0 * named.credentials >> cred
    1 * errors.rejectValue('context.account', 'context.account.empty')
  }

  def "valid account, invalid credentials"() {
    given:
    def named = Mock(AccountCredentials)
    def cred = new Object()
    String account = 'account'

    when:
    boolean actual = validator.validateCredentials(account, accountProvider)

    then:
    !actual
    1 * accountProvider.getCredentials(account) >> named
    1 * named.credentials >> cred
    1 * errors.rejectValue('context.account', 'context.account.notFound')
  }

  def "ValidateUUID"() {
    when:
    boolean actual = validator.validateUUID(value, 'test')

    then:
    actual == result
    if (!result) {
      1 * errors.rejectValue('context.test', msg)
    }

    where:
    value                                  | result | msg
    '62e3b610-281a-11e6-bdf4-0800200c9a66' | true   | 'context.test.notUUID'
    '123'                                  | false  | 'context.test.notUUID'
    ''                                     | false  | 'context.test.empty'
    null                                   | false  | 'context.test.empty'
  }

  def "ValidateCIDR"() {
    when:
    boolean actual = validator.validateCIDR(value, 'test')

    then:
    actual == result
    if (!result) {
      1 * errors.rejectValue('context.test', 'context.test.invalidCIDR')
    }

    where:
    value       | result
    '0.0.0.0/0' | true
    '0.0.:0'    | false
  }

  def "ValidateCIDR - Empty"() {
    when:
    boolean actual = validator.validateCIDR('', 'test')

    then:
    !actual
    1 * errors.rejectValue('context.test', 'context.test.empty')
  }

  def "ValidateRuleType"() {
    when:
    boolean actual = validator.validateRuleType(value, 'test')

    then:
    actual == result
    if (!result) {
      1 * errors.rejectValue('context.test', _)
    }

    where:
    value | result
    ''    | false
    'UDP' | false
    'TCP' | true
  }

  def "ValidateHttpMethod"() {
    when:
    boolean actual = validator.validateHttpMethod(value, 'test')

    then:
    actual == result
    if (!result) {
      validator.errors.getFieldError('context.test')?.rejectedValue == expectedRejectedValue
    }

    where:
    value    | result | expectedRejectedValue
    'GET'    | true   | ''
    'GETTER' | false  | 'context.test.invalidHttpMethod'
    ''       | false  | 'context.test.empty'
  }

  def "ValidateHttpStatus"() {
    when:
    boolean actual = validator.validateHttpStatusCode(value, 'test')

    then:
    actual == result
    if (!result) {
      validator.errors.getFieldError('context.test')?.rejectedValue == expectedRejectedValue
    }

    where:
    value | result | expectedRejectedValue
    200   | true   | ''
    199   | false  | 'context.test.invalidHttpStatusCode'
    null  | false  | 'context.test.invalidHttpStatusCode'
  }

  def "ValidateURL"() {
    when:
    boolean actual = validator.validateURI(value, 'test')

    then:
    actual == result
    if (!result) {
      validator.errors.getFieldError('context.test')?.rejectedValue == expectedRejectedValue
    }

    where:
    value                   | result | expectedRejectedValue
    'http://www.goggle.com' | true   | ''
    '/test'                 | true   | ''
    ''                      | false  | 'context.test.empty'
  }

  def "ValidateGreaterThan"() {
    when:
    boolean actual = validator.validateGreaterThan(subject, other, "test")

    then:
    actual == result
    if (!result) {
      validator.errors.getFieldError('context.test')?.rejectedValue == expectedRejectedValue
    }

    where:
    subject | other | result | expectedRejectedValue
    1       | 0     | true   | ''
    2       | 2     | false  | ''
    3       | 4     | false  | 'context.test.empty'
  }
}
