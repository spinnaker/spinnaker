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

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators.loadbalancer

import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.DeleteOpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class DeleteOpenstackLoadBalancerDescriptionValidatorSpec extends Specification {

  Errors errors
  AccountCredentialsProvider provider
  DeleteOpenstackLoadBalancerDescriptionValidator validator
  OpenstackNamedAccountCredentials credentials
  @Shared
  OpenstackCredentials credz
  OpenstackClientProvider clientProvider

  def setup() {
    clientProvider = Mock(OpenstackClientProvider)
    clientProvider.getProperty('allRegions') >> ['r1']
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
  }

  def "Validate no exception"() {
    given:
    validator = new DeleteOpenstackLoadBalancerDescriptionValidator(accountCredentialsProvider: provider)
    DeleteOpenstackLoadBalancerDescription description = new DeleteOpenstackLoadBalancerDescription(account: 'foo', region: 'r1', id: UUID.randomUUID().toString(), credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  @Unroll
  def "Validate empty required fields exception"() {
    given:
    validator = new DeleteOpenstackLoadBalancerDescriptionValidator(accountCredentialsProvider: provider)
    DeleteOpenstackLoadBalancerDescription description = new DeleteOpenstackLoadBalancerDescription(account: 'a', region: 'r1', credentials: credz, id: '')

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)

  }
}
