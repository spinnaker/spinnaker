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

import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.OpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification

class OpenstackDescriptionValidatorSpec extends Specification {

  Errors errors
  AccountCredentialsProvider provider
  FooValidator validator
  OpenstackNamedAccountCredentials credentials
  OpenstackCredentials credz
  OpenstackClientProvider clientProvider

  def "Validate no exception"() {
    clientProvider = Mock(OpenstackClientProvider)
    clientProvider.getProperty('allRegions') >> ['r1']
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackProviderFactory.createProvider(credentials) >> clientProvider
    credz = new OpenstackCredentials(credentials)
    errors = Mock(Errors)
    credentials = Mock(OpenstackNamedAccountCredentials) {
      1 * getCredentials() >> credz
    }
    provider = Mock(AccountCredentialsProvider) {
      1 * getCredentials(_) >> credentials
    }
    validator = new FooValidator<>(accountCredentialsProvider: provider)
    OpenstackInstancesDescription description = new OpenstackInstancesDescription(account: 'test', instanceIds: ['1','2'], credentials: credz, region: 'r1')

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_,_)
  }

  def "Validate empty account exception"() {
    given:
    credz = Mock(OpenstackCredentials)
    credentials = Mock(OpenstackNamedAccountCredentials) {
      0 * getCredentials() >> credz
    }
    provider = Mock(AccountCredentialsProvider) {
      0 * getCredentials(_) >> credentials
    }
    errors = Mock(Errors)
    validator = new FooValidator<>(accountCredentialsProvider: provider)
    OpenstackInstancesDescription description = new OpenstackInstancesDescription(account: '', instanceIds: ['1','2'])

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  def "Validate invalid region exception"() {
    clientProvider = Mock(OpenstackClientProvider)
    clientProvider.getProperty('allRegions') >> ['r1']
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackProviderFactory.createProvider(credentials) >> clientProvider
    credz = new OpenstackCredentials(credentials)
    errors = Mock(Errors)
    credentials = Mock(OpenstackNamedAccountCredentials) {
      1 * getCredentials() >> credz
    }
    provider = Mock(AccountCredentialsProvider) {
      1 * getCredentials(_) >> credentials
    }
    validator = new FooValidator<>(accountCredentialsProvider: provider)
    OpenstackInstancesDescription description = new OpenstackInstancesDescription(account: 'test', instanceIds: ['1','2'], credentials: credz, region: 'r2')

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  def "Validate empty region exception"() {
    clientProvider = Mock(OpenstackClientProvider)
    clientProvider.getProperty('allRegions') >> ['r1']
    GroovyMock(OpenstackProviderFactory, global: true)
    OpenstackProviderFactory.createProvider(credentials) >> clientProvider
    credz = new OpenstackCredentials(credentials)
    errors = Mock(Errors)
    credentials = Mock(OpenstackNamedAccountCredentials) {
      1 * getCredentials() >> credz
    }
    provider = Mock(AccountCredentialsProvider) {
      1 * getCredentials(_) >> credentials
    }
    validator = new FooValidator<>(accountCredentialsProvider: provider)
    OpenstackInstancesDescription description = new OpenstackInstancesDescription(account: 'test', instanceIds: ['1','2'], credentials: credz, region: '')

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  static class FooValidator extends AbstractOpenstackDescriptionValidator<OpenstackAtomicOperationDescription> {

    @Override
    void validate(OpenstackAttributeValidator validator, List priorDescriptions, OpenstackAtomicOperationDescription description, Errors errors) {
    }

    @Override
    String getContext() {
      "foo"
    }
  }

}
