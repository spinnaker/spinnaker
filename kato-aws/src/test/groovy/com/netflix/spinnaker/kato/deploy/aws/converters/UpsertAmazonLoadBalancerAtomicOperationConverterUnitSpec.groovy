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


package com.netflix.spinnaker.kato.deploy.aws.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.kato.deploy.aws.description.UpsertAmazonLoadBalancerDescription
import com.netflix.spinnaker.kato.deploy.aws.ops.loadbalancer.UpsertAmazonLoadBalancerAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

class UpsertAmazonLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  @Shared
  UpsertAmazonLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertAmazonLoadBalancerAtomicOperationConverter(objectMapper: new ObjectMapper())
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(NetflixAmazonCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "basicAmazonDeployDescription type returns BasicAmazonDeployDescription and DeployAtomicOperation"() {
    setup:
    def input = [name: "kato-main", availabilityZones: ["us-east-1": ["us-east-1a"]],
                 listeners  :
                   [[externalProtocol: "HTTP", internalProtocol: "HTTP", externalPort: 7001, internalPort: 7001]],
                 credentials: "test"]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof UpsertAmazonLoadBalancerDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof UpsertAmazonLoadBalancerAtomicOperation
  }
}
