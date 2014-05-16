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

package com.netflix.bluespar.kato.deploy.aws.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.bluespar.kato.deploy.aws.description.CreateAmazonLoadBalancerDescription
import com.netflix.bluespar.kato.deploy.aws.converters.CreateAmazonLoadBalancerAtomicOperationConverter
import com.netflix.bluespar.kato.deploy.aws.ops.loadbalancer.CreateAmazonLoadBalancerAtomicOperation
import com.netflix.bluespar.kato.security.NamedAccountCredentials
import com.netflix.bluespar.kato.security.NamedAccountCredentialsHolder
import spock.lang.Shared
import spock.lang.Specification

class CreateAmazonLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  @Shared
  CreateAmazonLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new CreateAmazonLoadBalancerAtomicOperationConverter(objectMapper: new ObjectMapper())
    def namedAccountCredentialsHolder = Mock(NamedAccountCredentialsHolder)
    def mockCredentials = Mock(NamedAccountCredentials)
    namedAccountCredentialsHolder.getCredentials(_) >> mockCredentials
    converter.namedAccountCredentialsHolder = namedAccountCredentialsHolder
  }

  void "basicAmazonDeployDescription type returns BasicAmazonDeployDescription and DeployAtomicOperation"() {
    setup:
    def input = [clusterName: "kato-main", availabilityZones: ["us-east-1": ["us-east-1a"]],
                 listeners  :
                   [[externalProtocol: "HTTP", internalProtocol: "HTTP", externalPort: 7001, internalPort: 7001]],
                 credentials: "test"]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof CreateAmazonLoadBalancerDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof CreateAmazonLoadBalancerAtomicOperation
  }
}
