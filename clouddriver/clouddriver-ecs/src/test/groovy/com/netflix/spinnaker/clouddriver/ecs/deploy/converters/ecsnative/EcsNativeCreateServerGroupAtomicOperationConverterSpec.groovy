/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.converters.ecsnative

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeCreateServerGroupDescription
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.EcsNativeCreateServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class EcsNativeCreateServerGroupAtomicOperationConverterSpec extends Specification {
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)

  def 'should convert to the ecs-native description and operation'() {
    given:
    def objectMapper = new ObjectMapper()
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    def converter = new EcsNativeCreateServerGroupAtomicOperationConverter(objectMapper: objectMapper)
    converter.accountCredentialsProvider = accountCredentialsProvider

    def input = [
      ecsClusterName    : 'mycluster',
      iamRole           : 'role-arn',
      containerPort     : 1337,
      targetGroup       : 'target-group-arn',
      securityGroups    : ['sg-deadbeef'],
      serverGroupVersion: 'v007',
      portProtocol      : 'tc',
      computeUnits      : 256,
      reservedMemory    : 512,
      dockerImageAddress: 'docker-url',
      capacity          : new ServerGroup.Capacity(0, 2, 1),
      availabilityZones : ['us-west-1': ['us-west-1a']],
      region            : 'us-west-1',
      deploymentStrategy: 'BLUE_GREEN',
      credentials       : 'test'
    ]

    accountCredentialsProvider.getCredentials(_) >> TestCredential.named('test')

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof EcsNativeCreateServerGroupDescription
    description.getDeploymentStrategy() == 'BLUE_GREEN'

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof EcsNativeCreateServerGroupAtomicOperation
  }
}
