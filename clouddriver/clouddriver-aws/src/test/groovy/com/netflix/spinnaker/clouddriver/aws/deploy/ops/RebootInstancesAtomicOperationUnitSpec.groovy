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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.RebootInstancesRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.RebootInstancesDescription
import spock.lang.Specification

class RebootInstancesAtomicOperationUnitSpec extends Specification {
  def mockAmazonEC2 = Mock(Ec2Client)
  def mockAmazonClientProvider = Mock(AmazonClientProvider) {
    getAmazonEC2V2(_, _) >> mockAmazonEC2
  }

  def setup() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should reboot instances"() {
    setup:
    def description = new RebootInstancesDescription(
        region: region, instanceIds: instanceIds, credentials: TestCredential.named('test')
    )
    def operation = new RebootInstancesAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    with(mockAmazonEC2) {
      0 * _
      1 * rebootInstances(RebootInstancesRequest.builder().instanceIds(instanceIds).build())
    }

    where:
    region = "us-west-1"
    instanceIds = ["i-123", "i-456"]
  }
}
