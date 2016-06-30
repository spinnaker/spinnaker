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

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.CloneOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ResizeOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification

class CloneOpenstackAtomicOperationValidatorSpec extends Specification {

  Errors errors
  AccountCredentialsProvider provider
  ResizeOpenstackAtomicOperationValidator validator
  OpenstackNamedAccountCredentials credentials
  OpenstackCredentials credz
  OpenstackClientProvider clientProvider

  String account = 'foo'
  String application = 'app1'
  String region = 'r1'
  String stack = 'stack1'

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
    validator = new ResizeOpenstackAtomicOperationValidator(accountCredentialsProvider: provider)
  }

  def "Validate - no error"() {
    given:
    ResizeOpenstackAtomicOperationDescription description = new ResizeOpenstackAtomicOperationDescription(serverGroupName: 'from', region: 'r1', credentials: credz, account: account, capacity: new ResizeOpenstackAtomicOperationDescription.Capacity(max: 5, min: 3))

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_,_)
  }

  def "Validate invalid sizing"() {
    given:
    ResizeOpenstackAtomicOperationDescription description = new ResizeOpenstackAtomicOperationDescription(serverGroupName: 'from', region: 'r1', credentials: credz, account: account, capacity: new ResizeOpenstackAtomicOperationDescription.Capacity(max: 3, min: 4))

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

}
