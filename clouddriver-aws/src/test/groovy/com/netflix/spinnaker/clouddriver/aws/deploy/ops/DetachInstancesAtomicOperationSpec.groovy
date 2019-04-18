/*
 * Copyright 2015 Netflix, Inc.
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
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.DetachInstancesRequest
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.autoscaling.model.LifecycleState
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreateTagsRequest
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DetachInstancesAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DetachInstancesDescription
import spock.lang.Specification

class DetachInstancesAtomicOperationSpec extends Specification {
  def amazonAutoScaling = Mock(AmazonAutoScaling)
  def amazonEC2 = Mock(AmazonEC2)
  def amazonClientProvider = Mock(AmazonClientProvider)

  void setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should only detach instances that are associated with the ASG"() {
    given:
    def description = new DetachInstancesDescription(
      instanceIds: ["i-000001", "i-000002"],
      terminateDetachedInstances: true,
      decrementDesiredCapacity: true
    )

    and:
    def operation = new DetachInstancesAtomicOperation(description)
    operation.amazonClientProvider = amazonClientProvider

    when:
    operation.operate([])

    then:
    1 * amazonClientProvider.getAutoScaling(null, null, true) >> { amazonAutoScaling }
    1 * amazonClientProvider.getAmazonEC2(null, null, true) >> { amazonEC2 }
    1 * amazonAutoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
        [new AutoScalingGroup().withInstances(
          new Instance().withInstanceId("i-000001").withLifecycleState(LifecycleState.Standby)
        ).withMinSize(1).withDesiredCapacity(2)]
      )
    }
    1 * amazonEC2.createTags({ CreateTagsRequest request ->
      request.resources == ["i-000001"] && request.tags*.key.containsAll(["spinnaker:PendingTermination", "spinnaker:Detached"])
    } as CreateTagsRequest)
    1 * amazonEC2.terminateInstances({ TerminateInstancesRequest request ->
      request.instanceIds == ["i-000001"]
    } as TerminateInstancesRequest)
    1 * amazonAutoScaling.detachInstances({ DetachInstancesRequest request ->
      request.instanceIds == ["i-000001"] && request.shouldDecrementDesiredCapacity
    } as DetachInstancesRequest)
    0 * _
  }

  void "should not terminate or decrement desired capacity unless explicitly specified"() {
    given:
    def description = new DetachInstancesDescription(
      instanceIds: ["i-000001", "i-000002"],
      terminateDetachedInstances: false,
      decrementDesiredCapacity: false
    )

    and:
    def operation = new DetachInstancesAtomicOperation(description)
    operation.amazonClientProvider = amazonClientProvider

    when:
    operation.operate([])

    then:
    1 * amazonClientProvider.getAutoScaling(null, null, true) >> { amazonAutoScaling }
    1 * amazonClientProvider.getAmazonEC2(null, null, true) >> { amazonEC2 }
    1 * amazonEC2.createTags({ CreateTagsRequest request ->
      request.resources == ["i-000001"] && request.tags*.key.containsAll(["spinnaker:Detached"])
    } as CreateTagsRequest)
    1 * amazonAutoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
        [new AutoScalingGroup().withInstances(
          new Instance().withInstanceId("i-000001").withLifecycleState(LifecycleState.InService)
        ).withMinSize(1).withDesiredCapacity(2)]
      )
    }
    1 * amazonAutoScaling.detachInstances({ DetachInstancesRequest request ->
      request.instanceIds == ["i-000001"] && !request.shouldDecrementDesiredCapacity
    } as DetachInstancesRequest)
    0 * _
  }

  void "should adjust minSize if specified (and required)"() {
    given:
    def description = new DetachInstancesDescription(
      instanceIds: ["i-000001", "i-000002"],
      terminateDetachedInstances: false,
      decrementDesiredCapacity: true,
      adjustMinIfNecessary: true
    )

    and:
    def operation = new DetachInstancesAtomicOperation(description)
    operation.amazonClientProvider = amazonClientProvider

    when:
    operation.operate([])

    then:
    1 * amazonClientProvider.getAutoScaling(null, null, true) >> { amazonAutoScaling }
    1 * amazonClientProvider.getAmazonEC2(null, null, true) >> { amazonEC2 }
    1 * amazonAutoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
        [new AutoScalingGroup().withInstances(
          new Instance().withInstanceId("i-000001").withLifecycleState(LifecycleState.InService)
        ).withMinSize(1).withDesiredCapacity(1)]
      )
    }
    1 * amazonEC2.createTags({ CreateTagsRequest request ->
      request.resources == ["i-000001"] && request.tags*.key.containsAll(["spinnaker:Detached"])
    } as CreateTagsRequest)
    1 * amazonAutoScaling.updateAutoScalingGroup({ UpdateAutoScalingGroupRequest request ->
      request.minSize == 0
    } as UpdateAutoScalingGroupRequest)
    1 * amazonAutoScaling.detachInstances({ DetachInstancesRequest request ->
      request.instanceIds == ["i-000001"] && request.shouldDecrementDesiredCapacity
    } as DetachInstancesRequest)
    0 * _
  }

  void "should fail if minSize adjustment is necessary but not allowed"() {
    given:
    def description = new DetachInstancesDescription(
      instanceIds: ["i-000001", "i-000002"],
      decrementDesiredCapacity: true
    )

    and:
    def operation = new DetachInstancesAtomicOperation(description)
    operation.amazonClientProvider = amazonClientProvider

    when:
    operation.operate([])

    then:
    1 * amazonClientProvider.getAutoScaling(null, null, true) >> { amazonAutoScaling }
    1 * amazonAutoScaling.describeAutoScalingGroups(_) >> {
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
        [new AutoScalingGroup().withInstances(
          new Instance().withInstanceId("i-000001").withLifecycleState(LifecycleState.InService),
          new Instance().withInstanceId("i-000002").withLifecycleState(LifecycleState.Pending)
        ).withMinSize(1).withDesiredCapacity(1)
        ]
      )
    }
    thrown(IllegalStateException)
  }
}
