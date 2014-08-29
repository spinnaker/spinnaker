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


package com.netflix.spinnaker.kato.deploy.aws.ops

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.*
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAamzonCredentials
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.aws.description.DestroyAsgDescription
import spock.lang.Specification

import java.lang.Void as Should

class DestroyAsgAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  Should "get list of instances and execute a terminate and decrement operation against them"() {
    setup:
    def description = new DestroyAsgDescription(asgName: "my-stack-v000", regions: ["us-east-1"], credentials: new NetflixAssumeRoleAamzonCredentials(name: "baz"))
    def provider = Mock(AmazonClientProvider)
    def mockAutoScaling = Mock(AmazonAutoScaling)
    provider.getAutoScaling(_, _) >> mockAutoScaling
    def op = new DestroyAsgAtomicOperation(description)
    op.amazonClientProvider = provider
    def mock = Mock(AutoScalingGroup)
    def inst = Mock(Instance)
    inst.getInstanceId() >> "i-123456"
    mock.getInstances() >> [inst]
    def describeResult1 = new DescribeAutoScalingGroupsResult().withAutoScalingGroups(mock)
    def mock2 = Mock(AutoScalingGroup)
    mock2.getInstances() >> []
    def describeResult2 = new DescribeAutoScalingGroupsResult().withAutoScalingGroups(mock2)

    when:
    op.operate([])

    then:
    2 * mockAutoScaling.describeAutoScalingGroups(_) >>> [describeResult1, describeResult2]
    1 * mockAutoScaling.updateAutoScalingGroup(_) >> { UpdateAutoScalingGroupRequest request ->
      assert !request.desiredCapacity
      assert !request.minSize
      assert !request.maxSize
    }
    1 * mockAutoScaling.terminateInstanceInAutoScalingGroup(_) >> { TerminateInstanceInAutoScalingGroupRequest request ->
      assert request.instanceId == "i-123456"
    }
    1 * mockAutoScaling.deleteAutoScalingGroup(_) >> { DeleteAutoScalingGroupRequest request ->
      assert request.autoScalingGroupName == "my-stack-v000"
    }
  }
}
