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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators.securitygroup

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.OpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.securitygroup.UpsertOpenstackSecurityGroupDescriptionValidator
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification

class UpsertOpenstackSecurityGroupDescriptionValidatorSpec extends Specification {

  Errors errors
  AccountCredentialsProvider provider
  UpsertOpenstackSecurityGroupDescriptionValidator validator
  OpenstackNamedAccountCredentials namedAccountCredentials
  OpenstackCredentials credentials

  def setup() {
    credentials = Mock(OpenstackCredentials)
    namedAccountCredentials = Mock(OpenstackNamedAccountCredentials) {
      1 * getCredentials() >> credentials
    }
    provider = Mock(AccountCredentialsProvider) {
      1 * getCredentials(_) >> namedAccountCredentials
    }
    errors = Mock(Errors)
    validator = new UpsertOpenstackSecurityGroupDescriptionValidator(accountCredentialsProvider: provider)
  }

  def "validate no rules"() {
    setup:
    def id = UUID.randomUUID().toString()
    def name = "name"
    def desc = "description"
    def description = new OpenstackSecurityGroupDescription(account: 'foo', id: id, name: name, description: desc, rules: [])

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  def "validate with rules"() {
    setup:
    def id = UUID.randomUUID().toString()
    def name = "name"
    def desc = "description"
    def rules = [
      new OpenstackSecurityGroupDescription.Rule(fromPort: 80, toPort: 80, cidr: "0.0.0.0/0"),
      new OpenstackSecurityGroupDescription.Rule(fromPort: 443, toPort: 443, cidr: "0.0.0.0/0")
    ]
    def description = new OpenstackSecurityGroupDescription(account: 'foo', id: id, name: name, description: desc, rules: rules)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  def "validate with invalid id"() {
    setup:
    def id = "not a uuid"
    def name = "name"
    def desc = "description"
    def description = new OpenstackSecurityGroupDescription(account: 'foo', id: id, name: name, description: desc, rules: [])

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('upsertOpenstackSecurityGroupAtomicOperationDescription.id', 'upsertOpenstackSecurityGroupAtomicOperationDescription.id.notUUID')
  }

  def "validate with without id is valid"() {
    setup:
    def name = "name"
    def desc = "description"
    def description = new OpenstackSecurityGroupDescription(account: 'foo', id: null, name: name, description: desc, rules: [])

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  def "validate with invalid rule"() {
    setup:
    def id = UUID.randomUUID().toString()
    def name = "name"
    def desc = "description"
    def rules = [
      new OpenstackSecurityGroupDescription.Rule(fromPort: fromPort, toPort: toPort, cidr: cidr)
    ]
    def description = new OpenstackSecurityGroupDescription(account: 'foo', id: id, name: name, description: desc, rules: rules)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_, rejectValue)

    where:
    fromPort | toPort | cidr      | rejectValue
    80       | 80     | '0.0.0.0' | 'upsertOpenstackSecurityGroupAtomicOperationDescription.cidr.invalidCIDR'
    80       | 80     | null      | 'upsertOpenstackSecurityGroupAtomicOperationDescription.cidr.empty'
    0        | 80     | '0.0.0.0/0' | 'upsertOpenstackSecurityGroupAtomicOperationDescription.fromPort.invalid (Must be in range [1, 65535])'
    80       | 0      | '0.0.0.0/0' | 'upsertOpenstackSecurityGroupAtomicOperationDescription.toPort.invalid (Must be in range [1, 65535])'
  }
}
