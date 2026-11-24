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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeUpdateServiceDescription
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.EcsNativeUpdateServiceAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class EcsNativeUpdateServiceAtomicOperationConverterSpec extends Specification {
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)

  def 'should convert to the ecs-native update-service description and operation'() {
    given:
    def converter = new EcsNativeUpdateServiceAtomicOperationConverter(objectMapper: new ObjectMapper())
    converter.accountCredentialsProvider = accountCredentialsProvider

    def input = [
      serverGroupName   : 'myapp-kcats-liated-v007',
      region            : 'us-west-1',
      taskDefinition    : 'task-def-arn',
      deploymentStrategy: 'BLUE_GREEN',
      bakeTimeInMinutes : 15,
      credentials       : 'test'
    ]

    accountCredentialsProvider.getCredentials(_) >> TestCredential.named('test')

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof EcsNativeUpdateServiceDescription
    description.getServerGroupName() == input['serverGroupName']
    description.getDeploymentStrategy() == 'BLUE_GREEN'
    description.getBakeTimeInMinutes() == 15

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof EcsNativeUpdateServiceAtomicOperation
  }
}
