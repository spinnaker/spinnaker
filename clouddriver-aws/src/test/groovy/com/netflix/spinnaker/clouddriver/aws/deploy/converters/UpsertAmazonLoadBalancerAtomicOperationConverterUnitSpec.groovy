/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.deploy.converters.UpsertAmazonLoadBalancerAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerClassicDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerV2Description
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerV2AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.UpsertAmazonLoadBalancerAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

class UpsertAmazonLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  @Shared
  UpsertAmazonLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials('test') >> Stub(NetflixAmazonCredentials)
    }
    this.converter = new UpsertAmazonLoadBalancerAtomicOperationConverter(objectMapper: new ObjectMapper(), accountCredentialsProvider: accountCredentialsProvider)
  }

  void "UpsertAmazonLoadBalancerAtomicOperationConverter convertDescription with no type returns UpsertAmazonLoadBalancerClassicDescription"() {
    setup:
    def input = [name: "kato-main", availabilityZones: ["us-east-1": ["us-east-1a"]],
                 listeners  :
                   [[externalProtocol: "HTTP", internalProtocol: "HTTP", externalPort: 7001, internalPort: 7001]],
                 credentials: "test"]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof UpsertAmazonLoadBalancerClassicDescription
  }

  void "UpsertAmazonLoadBalancerAtomicOperationConverter convertOperation with no type returns UpsertAmazonLoadBalancerAtomicOperation"() {
    setup:
    def input = [name: "kato-main", availabilityZones: ["us-east-1": ["us-east-1a"]],
                 listeners  :
                   [[externalProtocol: "HTTP", internalProtocol: "HTTP", externalPort: 7001, internalPort: 7001]],
                 credentials: "test"]

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof UpsertAmazonLoadBalancerAtomicOperation
  }

  void "UpsertAmazonLoadBalancerAtomicOperationConverter convertDescription with classic type returns UpsertAmazonLoadBalancerV2Description"() {
    setup:
    def input = [name: "kato-main", availabilityZones: ["us-east-1": ["us-east-1a"]], loadBalancerType: "classic",
                 listeners  :
                   [[externalProtocol: "HTTP", internalProtocol: "HTTP", externalPort: 7001, internalPort: 7001]],
                 credentials: "test"]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof UpsertAmazonLoadBalancerClassicDescription
  }

  void "UpsertAmazonLoadBalancerAtomicOperationConverter convertOperation with classic type returns UpsertAmazonLoadBalancerAtomicOperation"() {
    setup:
    def input = [name: "kato-main", availabilityZones: ["us-east-1": ["us-east-1a"]], loadBalancerType: "classic",
                 listeners  :
                   [[externalProtocol: "HTTP", internalProtocol: "HTTP", externalPort: 7001, internalPort: 7001]],
                 credentials: "test"]

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof UpsertAmazonLoadBalancerAtomicOperation
  }

  void "UpsertAmazonLoadBalancerAtomicOperationConverter convertDescription with application type returns UpsertAmazonLoadBalancerV2Description"() {
    setup:
    def input = [name: "kato-main", availabilityZones: ["us-east-1": ["us-east-1a"]], loadBalancerType: "application",
                 listeners  :
                   [[externalProtocol: "HTTP", internalProtocol: "HTTP", externalPort: 7001, internalPort: 7001]],
                 credentials: "test"]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof UpsertAmazonLoadBalancerV2Description
  }

  void "UpsertAmazonLoadBalancerAtomicOperationConverter convertOperation with application type returns UpsertAmazonLoadBalancerV2AtomicOperation"() {
    setup:
    def input = [name: "kato-main", availabilityZones: ["us-east-1": ["us-east-1a"]], loadBalancerType: "application",
                 listeners  :
                   [[externalProtocol: "HTTP", internalProtocol: "HTTP", externalPort: 7001, internalPort: 7001]],
                 credentials: "test"]

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof UpsertAmazonLoadBalancerV2AtomicOperation
  }

  void "should coerce types properly in nested structures"() {
    setup:
    def input = [credentials: 'test', listeners: [[externalPort: "7001", internalPort: "7001"], [externalPort: 80, internalPort: "25"]]]

    when:
    def description = converter.convertDescription(input)

    then:
    description.listeners[0].internalPort == 7001
    description.listeners[0].externalPort == 7001
    description.listeners[1].internalPort == 25
    description.listeners[1].externalPort == 80
  }
}
