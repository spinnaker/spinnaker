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
package com.netflix.spinnaker.kato.aws.deploy.ops
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.TerminateInstancesDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject

class TerminateInstancesAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def mockAmazonEC2 = Mock(AmazonEC2)
  def mockAmazonClientProvider = Mock(AmazonClientProvider) {
    getAmazonEC2(_, _) >> mockAmazonEC2
  }

  void "should terminate instances"() {
    def description = new TerminateInstancesDescription(region: "us-west-1", instanceIds: ["i-123", "i-456"])
    description.credentials = TestCredential.named('baz')
    @Subject def operation = new TerminateInstancesAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    with(mockAmazonEC2) {
      0 * _
      1 * terminateInstances(new TerminateInstancesRequest(instanceIds: ["i-123", "i-456"]))
    }
  }
}
