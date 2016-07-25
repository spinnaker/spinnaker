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

import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.UpsertOpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class UpsertOpenstackSecurityGroupDescriptionValidatorSpec extends Specification {

  Errors errors
  AccountCredentialsProvider provider
  OpenstackNamedAccountCredentials credentials
  OpenstackClientProvider clientProvider
  @Shared
  OpenstackCredentials credz
  @Shared
  UpsertOpenstackSecurityGroupDescriptionValidator validator



  def setup() {
    clientProvider = Mock(OpenstackClientProvider) {
      getAllRegions() >> ['r1']
    }
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackProviderFactory.createProvider(credentials) >> clientProvider
    credz = new OpenstackCredentials(credentials)
    errors = Mock(Errors)
    credentials = Mock(OpenstackNamedAccountCredentials) {
      _ * getCredentials() >> credz
    }
    provider = Mock(AccountCredentialsProvider) {
      _ * getCredentials(_) >> credentials
    }
    validator = new UpsertOpenstackSecurityGroupDescriptionValidator(accountCredentialsProvider: provider)
  }

  def "validate no rules"() {
    setup:
    def id = UUID.randomUUID().toString()
    def name = 'name'
    def desc = 'description'
    def description = new UpsertOpenstackSecurityGroupDescription(account: 'foo', region: 'r1', id: id, name: name, description: desc, rules: [], credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  def "validate with rules"() {
    setup:
    def id = UUID.randomUUID().toString()
    def name = 'name'
    def desc = 'description'
    def rules = [
      new UpsertOpenstackSecurityGroupDescription.Rule(fromPort: 80, toPort: 80, cidr: '0.0.0.0/0', ruleType: 'TCP'),
      new UpsertOpenstackSecurityGroupDescription.Rule(fromPort: 443, toPort: 443, remoteSecurityGroupId: UUID.randomUUID().toString(), ruleType: 'UDP'),
      new UpsertOpenstackSecurityGroupDescription.Rule(icmpType: 2, icmpCode: 3, cidr: '0.0.0.0/0', ruleType: 'ICMP'),
    ]
    def description = new UpsertOpenstackSecurityGroupDescription(account: 'foo', region: 'r1', id: id, name: name, description: desc, rules: rules, credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  def "validate with invalid id"() {
    setup:
    def id = 'not a uuid'
    def name = 'name'
    def desc = 'description'
    def description = new UpsertOpenstackSecurityGroupDescription(account: 'foo', region: 'r1', id: id, name: name, description: desc, rules: [], credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("${validator.context}.id", "${validator.context}.id.notUUID")
  }

  def "validate without id is valid"() {
    setup:
    def name = 'name'
    def desc = 'description'
    def description = new UpsertOpenstackSecurityGroupDescription(account: 'foo', region: 'r1', id: null, name: name, description: desc, rules: [], credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  @Unroll
  def "validate with invalid rule"() {
    setup:
    def id = UUID.randomUUID().toString()
    def name = 'name'
    def desc = 'description'
    def rules = [
      new UpsertOpenstackSecurityGroupDescription.Rule(
        ruleType: ruleType,
        fromPort: fromPort,
        toPort: toPort,
        icmpType: icmpType,
        icmpCode: icmpCode,
        cidr: cidr,
        remoteSecurityGroupId: remoteGroupId
      )
    ]
    def description = new UpsertOpenstackSecurityGroupDescription(account: 'foo', region: 'r1', id: id, name: name, description: desc, rules: rules, credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_, { it.endsWith(rejectValue) })

    where:
    ruleType | fromPort | toPort | icmpType | icmpCode | cidr        | remoteGroupId | rejectValue
    'TCP'    | 80       | 80     | null     | null     | '0.0.0.0'   | null          | '.cidr.invalidCIDR'
    'TCP'    | 80       | 80     | null     | null     | null        | null          | '.cidr.empty'
    'TCP'    | -2       | 80     | null     | null     | '0.0.0.0/0' | null          | '.fromPort.notInRange (Must be in range [-1, 65535])'
    'TCP'    | 80       | -2     | null     | null     | '0.0.0.0/0' | null          | '.toPort.notInRange (Must be in range [-1, 65535])'
    'TCP'    | 80       | 80     | null     | null     | null        | 'abc'         | '.remoteSecurityGroupId.notUUID'
    'ICMP'   | null     | null   | -2       | 4        | '0.0.0.0/0' | null          | '.notInRange (Must be in range [-1, 255])'
    'ICMP'   | null     | null   | 8        | 256      | '0.0.0.0/0' | null          | '.notInRange (Must be in range [-1, 255])'
    'SSH'    | 2        | 2      | null     | null     | '0.0.0.0/0' | null          | '.invalidSecurityGroupRuleType'

  }
}
