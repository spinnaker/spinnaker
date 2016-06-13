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

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.DeleteOpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Unroll

class DeleteOpenstackSecurityGroupDescriptionValidatorSpec extends Specification {

  Errors errors
  AccountCredentialsProvider provider
  DeleteOpenstackSecurityGroupDescriptionValidator validator
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
    validator = new DeleteOpenstackSecurityGroupDescriptionValidator(accountCredentialsProvider: provider)
  }

  def "valid id"() {
    given:
    def id = UUID.randomUUID().toString()
    def description = new DeleteOpenstackSecurityGroupDescription(account: 'foo', region: 'west', id: id)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  def "missing region"() {
    given:
    def id = UUID.randomUUID().toString()
    def description = new DeleteOpenstackSecurityGroupDescription(account: 'foo', id: id)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_, DeleteOpenstackSecurityGroupDescriptionValidator.CONTEXT + '.region.empty')
  }

  @Unroll
  def "invalid ids"() {
    given:
    def description = new DeleteOpenstackSecurityGroupDescription(account: 'foo', id: id)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_, msg)

    where:
    id     | expected | msg
    null   | false    | DeleteOpenstackSecurityGroupDescriptionValidator.CONTEXT + '.id.empty'
    ''     | false    | DeleteOpenstackSecurityGroupDescriptionValidator.CONTEXT + '.id.empty'
    '1234' | false    | DeleteOpenstackSecurityGroupDescriptionValidator.CONTEXT + '.id.notUUID'
  }
}
