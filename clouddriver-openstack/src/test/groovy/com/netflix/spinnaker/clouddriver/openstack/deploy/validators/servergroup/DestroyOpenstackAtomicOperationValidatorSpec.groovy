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

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DestroyOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification


class DestroyOpenstackAtomicOperationValidatorSpec extends Specification {

  Errors errors
  AccountCredentialsProvider provider
  DestroyOpenstackAtomicOperationValidator validator
  OpenstackNamedAccountCredentials credentials
  OpenstackCredentials credz

  def "Validate no exception"() {
    given:
    credz = Mock(OpenstackCredentials)
    credentials = Mock(OpenstackNamedAccountCredentials) {
      1 * getCredentials() >> credz
    }
    provider = Mock(AccountCredentialsProvider) {
      1 * getCredentials(_) >> credentials
    }
    errors = Mock(Errors)
    validator = new DestroyOpenstackAtomicOperationValidator(accountCredentialsProvider: provider)
    DestroyOpenstackAtomicOperationDescription description = new DestroyOpenstackAtomicOperationDescription(account: 'foo', serverGroupName: 'foo', region: 'region')

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
    validator = new DestroyOpenstackAtomicOperationValidator(accountCredentialsProvider: provider)
    DestroyOpenstackAtomicOperationDescription description = new DestroyOpenstackAtomicOperationDescription(account: '', serverGroupName: 'foo', region: 'region')

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  def "Validate empty server group name exception"() {
    given:
    credz = Mock(OpenstackCredentials)
    credentials = Mock(OpenstackNamedAccountCredentials) {
      1 * getCredentials() >> credz
    }
    provider = Mock(AccountCredentialsProvider) {
      1 * getCredentials(_) >> credentials
    }
    errors = Mock(Errors)
    validator = new DestroyOpenstackAtomicOperationValidator(accountCredentialsProvider: provider)
    DestroyOpenstackAtomicOperationDescription description = new DestroyOpenstackAtomicOperationDescription(account: 'foo', serverGroupName: '', region: 'region')

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

  def "Validate empty region exception"() {
    given:
    credz = Mock(OpenstackCredentials)
    credentials = Mock(OpenstackNamedAccountCredentials) {
      1 * getCredentials() >> credz
    }
    provider = Mock(AccountCredentialsProvider) {
      1 * getCredentials(_) >> credentials
    }
    errors = Mock(Errors)
    validator = new DestroyOpenstackAtomicOperationValidator(accountCredentialsProvider: provider)
    DestroyOpenstackAtomicOperationDescription description = new DestroyOpenstackAtomicOperationDescription(account: 'foo', serverGroupName: 'foo', region: '')

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue(_,_)
  }

}
