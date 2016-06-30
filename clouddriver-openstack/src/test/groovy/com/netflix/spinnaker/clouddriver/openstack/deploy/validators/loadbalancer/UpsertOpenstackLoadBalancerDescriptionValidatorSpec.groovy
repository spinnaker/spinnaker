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
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerMethod
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerProtocol
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitor
import com.netflix.spinnaker.clouddriver.openstack.domain.PoolHealthMonitorType
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Unroll

import static UpsertOpenstackLoadBalancerAtomicOperationValidator.context

@Unroll
class UpsertOpenstackLoadBalancerDescriptionValidatorSpec extends Specification {

  Errors errors
  AccountCredentialsProvider provider
  UpsertOpenstackLoadBalancerAtomicOperationValidator validator
  OpenstackNamedAccountCredentials credentials
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

  def "Validate create load balancer no exceptions"() {
    given:
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator(accountCredentialsProvider: provider)
    OpenstackLoadBalancerDescription description = new OpenstackLoadBalancerDescription(account: 'foo'
      , region: 'r1'
      , name: 'name'
      , internalPort: 80
      , externalPort: 80
      , subnetId: UUID.randomUUID().toString()
      , method: LoadBalancerMethod.ROUND_ROBIN
      , protocol: LoadBalancerProtocol.HTTP
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
      , id: UUID.randomUUID().toString()
      , name: 'name'
      , internalPort: 80
      , method: LoadBalancerMethod.ROUND_ROBIN
      , credentials: credz)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

  def "Validate create missing required field - #attribute"() {
    given:
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator(accountCredentialsProvider: provider)
    Map<String, ?> inputMap = ['account'       : 'foo', 'region': 'r1', 'internalPort': 80
                               , 'externalPort': 80, 'subnetId': UUID.randomUUID().toString(), 'name': 'name'
                               , 'method'      : LoadBalancerMethod.ROUND_ROBIN, 'protocol': LoadBalancerProtocol.HTTP
                               , 'credentials': credz]
    inputMap.remove(attribute)
    OpenstackLoadBalancerDescription description = new OpenstackLoadBalancerDescription(inputMap)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("${validator.context}.${attribute}", _)

    where:
    attribute << ['name', 'region', 'internalPort', 'externalPort', 'subnetId', 'method', 'protocol']
  }

  def "Validate update missing required field - #attribute"() {
    given:
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator(accountCredentialsProvider: provider)
    Map<String, ?> inputMap = ['account': 'foo', 'region': 'r1', 'internalPort': 80, 'id': UUID.randomUUID().toString(),
                               'name'   : 'name', 'method': LoadBalancerMethod.ROUND_ROBIN, 'credentials': credz]
    inputMap.remove(attribute)
    OpenstackLoadBalancerDescription description = new OpenstackLoadBalancerDescription(inputMap)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("${validator.context}.${attribute}", _)

    where:
    attribute << ['name', 'region', 'internalPort', 'method']
  }

  def "Validate create invalid field "() {
    given:
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator(accountCredentialsProvider: provider)
    Map<String, ?> inputMap = ['account'       : 'foo', 'region': 'r1', 'internalPort': 80
                               , 'externalPort': 80, 'subnetId': UUID.randomUUID().toString(), 'name': 'name'
                               , 'method'      : LoadBalancerMethod.ROUND_ROBIN, 'protocol': LoadBalancerProtocol.HTTP
                               , 'credentials': credz]
    inputMap.put(attribute.key, attribute.value)
    OpenstackLoadBalancerDescription description = new OpenstackLoadBalancerDescription(inputMap)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("${validator.context}.${attribute.key}", _)

    where:
    attribute << ['name': '', 'region': '', 'internalPort': -1, 'externalPort': -1, 'subnetId': 'abc', 'method': null, 'protocol': null]
  }

  def "Validate update invalid field"() {
    given:
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator(accountCredentialsProvider: provider)
    Map<String, ?> inputMap = ['account': 'foo', 'region': 'r1', 'internalPort': 80, 'id': UUID.randomUUID().toString(),
                               'name'   : 'name', 'method': LoadBalancerMethod.ROUND_ROBIN, 'credentials': credz]
    inputMap.put(attribute.key, attribute.value)
    println(attribute.class)
    OpenstackLoadBalancerDescription description = new OpenstackLoadBalancerDescription(inputMap)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("${validator.context}.${attribute.key}", _)

    where:
    attribute << ['name': '', 'region': '', 'internalPort': -1, 'method': null, 'id': 'abc']
  }

  def "Validate health monitor values"() {
    given:
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator(accountCredentialsProvider: provider)
    OpenstackAttributeValidator attributeValidator = new OpenstackAttributeValidator(validator.context, errors)
    Map<String, ?> inputMap = ['type'        : PoolHealthMonitorType.HTTP, 'delay': 5, 'timeout': 5
                               , 'maxRetries': 5, 'httpMethod': 'GET', 'expectedHttpStatusCodes': [200]
                               , 'url'       : 'http://www.google.com']
    inputMap.put(attribute.key, attribute.value)
    PoolHealthMonitor poolHealthMonitor = new PoolHealthMonitor(inputMap)

    when:
    println "key ${attribute.key} value: ${attribute.value}"
    validator.validateHealthMonitor(attributeValidator, poolHealthMonitor)

    then:
    1 * errors.rejectValue("${validator.context}.${attribute.key}", _)

    where:
    attribute << [ 'type': null, 'delay': -1, 'timeout': -1, 'maxRetries': -1, 'httpMethod': 'test', 'expectedHttpStatusCodes': [20], 'url': '\\backslash']
  }

  def "Validate health monitor success"() {
    given:
    String URL = 'URL'
    List<Integer> expectedCodes = [100]
    int delay, timeout, maxRetries = 2
    String method = 'GET'
    validator = new UpsertOpenstackLoadBalancerAtomicOperationValidator()
    OpenstackAttributeValidator attributeValidator = Mock()
    PoolHealthMonitor poolHealthMonitor = Mock()

    when:
    validator.validateHealthMonitor(attributeValidator, poolHealthMonitor)

    then:
    1 * poolHealthMonitor.type >> PoolHealthMonitorType.HTTP
    1 * poolHealthMonitor.delay >> delay
    1 * poolHealthMonitor.timeout >> timeout
    1 * poolHealthMonitor.maxRetries >> maxRetries
    1 * attributeValidator.validatePositive(delay, _)
    1 * attributeValidator.validatePositive(timeout, _)
    1 * attributeValidator.validatePositive(maxRetries, _)
    2 * poolHealthMonitor.httpMethod >> method
    1 * attributeValidator.validateHttpMethod(method, _)
    2 * poolHealthMonitor.expectedHttpStatusCodes >> expectedCodes
    expectedCodes.size() * attributeValidator.validateHttpStatusCode(_, _)
    2 * poolHealthMonitor.url >> URL
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
    PoolHealthMonitor poolHealthMonitor = Mock()

    when:
    validator.validateHealthMonitor(attributeValidator, poolHealthMonitor)

    then:
    1 * poolHealthMonitor.type >> PoolHealthMonitorType.PING
    1 * poolHealthMonitor.delay >> delay
    1 * poolHealthMonitor.timeout >> timeout
    1 * poolHealthMonitor.maxRetries >> maxRetries
    1 * attributeValidator.validatePositive(delay, _)
    1 * attributeValidator.validatePositive(timeout, _)
    1 * attributeValidator.validatePositive(maxRetries, _)
    1 * poolHealthMonitor.httpMethod >> method
    0 * attributeValidator.validateHttpMethod(method, _)
    1 * poolHealthMonitor.expectedHttpStatusCodes >> expectedCodes
    0 * attributeValidator.validateHttpStatusCode(_, _)
    1 * poolHealthMonitor.url >> URL
    0 * attributeValidator.validateURI(URL, _)
  }

}
