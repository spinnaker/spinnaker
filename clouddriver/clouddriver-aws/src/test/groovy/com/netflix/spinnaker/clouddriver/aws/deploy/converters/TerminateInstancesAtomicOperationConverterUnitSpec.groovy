/*
 * Copyright 2026 spinnaker.io
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
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.TerminateInstancesDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.TerminateInstancesAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.model.AmazonInstance
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonInstanceProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class TerminateInstancesAtomicOperationConverterUnitSpec extends Specification {
  def amazonInstanceProvider = Mock(AmazonInstanceProvider)

  @Subject
  def converter = new TerminateInstancesAtomicOperationConverter(
    objectMapper: new ObjectMapper(),
    amazonInstanceProvider: amazonInstanceProvider,
    accountCredentialsProvider: Mock(AccountCredentialsProvider) {
      getCredentials("test") >> TestCredential.named("test")
    }
  )

  @Unroll
  void "terminates in the auto scaling group only when requested (#terminateInAutoScalingGroup)"() {
    given: "a stage context as orca forwards it, including keys the description does not declare"
    def input = [
      type         : "terminateInstances",
      cloudProvider: "aws",
      credentials  : "test",
      region       : "us-west-1",
      zone         : "us-west-1a",
      instanceIds  : ["i-123"]
    ]
    if (terminateInAutoScalingGroup != null) {
      input.terminateInAutoScalingGroup = terminateInAutoScalingGroup
    }
    amazonInstanceProvider.getInstance("test", "us-west-1", "i-123") >> new AmazonInstance(name: "i-123", serverGroup: "app-v000")

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof TerminateInstancesDescription
    description.instanceIds == ["i-123"]
    description.serverGroups == ["app-v000"] as Set
    description.terminateInAutoScalingGroup == expected

    and:
    converter.convertOperation(input) instanceof TerminateInstancesAtomicOperation

    where:
    terminateInAutoScalingGroup || expected
    null                        || false
    false                       || false
    true                        || true
  }
}
