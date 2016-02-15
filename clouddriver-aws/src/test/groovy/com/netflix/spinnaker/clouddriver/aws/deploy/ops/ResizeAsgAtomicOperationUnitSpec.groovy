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
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResizeAsgDescription
import spock.lang.Specification
import spock.lang.Unroll

class ResizeAsgAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  @Unroll
  void "should update ASG iff it exists and is not in the process of being deleted"() {
    setup:
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockAmazonClientProvider = Mock(AmazonClientProvider)
    mockAmazonClientProvider.getAutoScaling(_, _, true) >> mockAutoScaling
    def description = new ResizeAsgDescription(asgName: "myasg-stack-v000", regions: ["us-west-1"])
    description.credentials = TestCredential.named('baz')
    description.capacity.min = 1
    description.capacity.max = 2
    description.capacity.desired = 5
    def operation = new ResizeAsgAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> { DescribeAutoScalingGroupsRequest request ->
      assert request.autoScalingGroupNames == ["myasg-stack-v000"]
      return new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
        new AutoScalingGroup().withAutoScalingGroupName("myasg-stack-v0001").withStatus(status)
      )
    }
    expectedUpdateCount * mockAutoScaling.updateAutoScalingGroup(_) >> { UpdateAutoScalingGroupRequest request ->
      assert request.autoScalingGroupName == "myasg-stack-v000"
      assert request.minSize == 1
      assert request.maxSize == 2
      assert request.desiredCapacity == 5
    }
    0 * mockAutoScaling._

    where:
    status     || expectedUpdateCount
    "Deleting" || 0
    null       || 1
  }
}
