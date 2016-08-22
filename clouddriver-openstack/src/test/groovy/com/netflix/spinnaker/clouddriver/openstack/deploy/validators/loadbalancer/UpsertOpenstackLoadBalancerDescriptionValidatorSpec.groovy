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
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor.HealthMonitorType
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription.Listener
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.loadbalancer.OpenstackLoadBalancerDescription.Listener.ListenerType
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator
import com.netflix.spinnaker.clouddriver.openstack.domain.HealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Unroll



@Unroll
class UpsertOpenstackLoadBalancerDescriptionValidatorSpec extends Specification {

  Errors errors
  AccountCredentialsProvider provider
  UpsertOpenstackLoadBalancerAtomicOperationValidator validator
  OpenstackNamedAccountCredentials credentials
  OpenstackCredentials credz
  OpenstackClientProvider clientProvider

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
  }

  def "Validate create load balancer no exceptions"() {
    given:
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator(accountCredentialsProvider: provider)
    OpenstackLoadBalancerDescription description = new OpenstackLoadBalancerDescription(account: 'foo'
      , region: 'r1'
      , name: 'name'
      , subnetId: UUID.randomUUID().toString()
      , algorithm: OpenstackLoadBalancerDescription.Algorithm.ROUND_ROBIN
      , securityGroups: [UUID.randomUUID().toString()]
      , listeners: [ new Listener(externalPort: 80, externalProtocol: ListenerType.HTTP, internalPort: 8080, internalProtocol: ListenerType.HTTP)]
      , healthMonitor: new HealthMonitor(type: HealthMonitorType.PING, delay: 1, timeout: 1, maxRetries: 1)
      , credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  def "Validate update load balancer no exceptions"() {
    given:
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator(accountCredentialsProvider: provider)
    OpenstackLoadBalancerDescription description = new OpenstackLoadBalancerDescription(account: 'foo'
      , region: 'r1'
      , id : UUID.randomUUID().toString()
      , name: 'name'
      , subnetId: UUID.randomUUID().toString()
      , algorithm: OpenstackLoadBalancerDescription.Algorithm.ROUND_ROBIN
      , securityGroups: [UUID.randomUUID().toString()]
      , listeners: [ new Listener(externalPort: 80, externalProtocol: ListenerType.HTTP, internalPort: 8080, internalProtocol: ListenerType.HTTP)]
      , healthMonitor: new HealthMonitor(type: HealthMonitorType.PING, delay: 1, timeout: 1, maxRetries: 1)
      , credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  def "Validate missing required field - #attribute"() {
    given:
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator(accountCredentialsProvider: provider)
    Map<String, ?> inputMap = [account : 'foo'
                               , region: 'r1'
                               , name: 'name'
                               , subnetId: UUID.randomUUID().toString()
                               , algorithm: OpenstackLoadBalancerDescription.Algorithm.ROUND_ROBIN
                               , securityGroups: [UUID.randomUUID().toString()]
                               , listeners: [ new Listener(externalPort: 80, externalProtocol: ListenerType.HTTP, internalPort: 8080, internalProtocol: ListenerType.HTTP)]
                               , credentials: credz]
    inputMap.remove(attribute)
    OpenstackLoadBalancerDescription description = new OpenstackLoadBalancerDescription(inputMap)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("${validator.context}.${attribute}", _)

    where:
    attribute << ['name', 'region', 'subnetId', 'algorithm', 'securityGroups', 'listeners']
  }

  def "Validate invalid field - #attribute"() {
    given:
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator(accountCredentialsProvider: provider)
    Map<String, ?> inputMap = ['account'       : 'foo'
                               , region: 'r1'
                               , name: 'name'
                               , id : UUID.randomUUID().toString()
                               , subnetId: UUID.randomUUID().toString()
                               , algorithm: OpenstackLoadBalancerDescription.Algorithm.ROUND_ROBIN
                               , securityGroups: [UUID.randomUUID().toString()]
                               , listeners: [ new Listener(externalPort: 80, externalProtocol: ListenerType.HTTP, internalPort: 8080, internalProtocol: ListenerType.HTTP)]
                               , credentials: credz]
    inputMap.put(attribute.key, attribute.value)
    OpenstackLoadBalancerDescription description = new OpenstackLoadBalancerDescription(inputMap)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("${validator.context}.${attribute.key}", _)

    where:
    attribute << [name: '', region: '', name : '', id : 'abc', subnetId : null, algorithm: null, securityGroups: [], listeners: []]
  }

  def "Validate health monitor values - #attributes"() {
    given:
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator(accountCredentialsProvider: provider)
    OpenstackAttributeValidator attributeValidator = new OpenstackAttributeValidator(validator.context, errors)
    Map<String, ?> inputMap = ['delay': 5
                               , 'timeout': 5
                               , 'maxRetries': 5
                               , 'httpMethod': 'GET'
                               , 'expectedCodes': [200]
                               , 'url'       : 'http://www.google.com']
    inputMap.put(attribute.key, attribute.value)

    when:
    validator.validateHealthMonitor(attributeValidator, new HealthMonitor(inputMap))

    then:
    1 * errors.rejectValue("${validator.context}.${attribute.key}", _)

    where:
    attribute << [ 'type': null, 'delay': -1, 'timeout': -1, 'maxRetries': -1, 'httpMethod': 'test', 'expectedCodes': [20], 'url': '\\backslash']
  }

  def "Validate health monitor success"() {
    given:
    String URL = 'URL'
    List<Integer> expectedCodes = [100]
    int delay, timeout, maxRetries = 2
    String method = 'GET'
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator()
    OpenstackAttributeValidator attributeValidator = Mock()

    when:
    validator.validateHealthMonitor(attributeValidator, new HealthMonitor(delay: delay, timeout: timeout, maxRetries: maxRetries, httpMethod: method, url: URL, expectedCodes: expectedCodes))

    then:
    1 * attributeValidator.validatePositive(delay, _)
    1 * attributeValidator.validatePositive(timeout, _)
    1 * attributeValidator.validatePositive(maxRetries, _)
    1 * attributeValidator.validateHttpMethod(method, _)
    expectedCodes.size() * attributeValidator.validateHttpStatusCode(_, _)
    1 * attributeValidator.validateURI(URL, _)
  }

  def "Validate health monitor success without options"() {
    given:
    String URL = null
    List<Integer> expectedCodes = null
    int delay, timeout, maxRetries = 2
    String method = null
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator()
    OpenstackAttributeValidator attributeValidator = Mock()

    when:
    validator.validateHealthMonitor(attributeValidator, new HealthMonitor(delay: delay, timeout: timeout, maxRetries: maxRetries, httpMethod: method, url: URL, expectedCodes: expectedCodes))

    then:
    1 * attributeValidator.validatePositive(delay, _)
    1 * attributeValidator.validatePositive(timeout, _)
    1 * attributeValidator.validatePositive(maxRetries, _)
    0 * attributeValidator.validateHttpMethod(method, _)
    0 * attributeValidator.validateHttpStatusCode(_, _)
    0 * attributeValidator.validateURI(URL, _)
  }

}
