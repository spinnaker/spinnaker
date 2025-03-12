/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AttachClassicLinkVpcRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AttachClassicLinkVpcDescription
import spock.lang.Specification
import spock.lang.Subject

class AttachClassicLinkVpcAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def mockAmazonEC2 = Mock(AmazonEC2)
  def mockAmazonClientProvider = Mock(AmazonClientProvider) {
    getAmazonEC2(_, _, true) >> mockAmazonEC2
  }

  void "should attach VPC to instance"() {
    def description = new AttachClassicLinkVpcDescription(region: "us-west-1", instanceId: "i-123", vpcId: "vpc-123",
      securityGroupIds: ["sg-123", "sg-456"])
    description.credentials = TestCredential.named('baz')
    @Subject def operation = new AttachClassicLinkVpcAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    with(mockAmazonEC2) {
      0 * _
      1 * attachClassicLinkVpc(new AttachClassicLinkVpcRequest(instanceId: "i-123", vpcId: "vpc-123",
        groups: ["sg-123", "sg-456"]))
    }
  }
}
