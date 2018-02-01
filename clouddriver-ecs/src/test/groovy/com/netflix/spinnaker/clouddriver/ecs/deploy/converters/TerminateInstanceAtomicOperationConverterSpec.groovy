/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.converters

import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.TerminateInstancesDescription
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.TerminateInstancesAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class TerminateInstanceAtomicOperationConverterSpec extends Specification {
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)

  def 'should convert'() {
    given:
    def converter = new TerminateInstancesAtomicOperationConverter()
    converter.accountCredentialsProvider = accountCredentialsProvider

    def instanceIds = ['id-1', 'id-2']
    def input = [instanceIds: instanceIds, region: 'us-west-1', credentials: 'test']

    accountCredentialsProvider.getCredentials(_) >> TestCredential.named('test')

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof TerminateInstancesDescription
    description.getEcsTaskIds() == instanceIds

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof TerminateInstancesAtomicOperation
  }
}
