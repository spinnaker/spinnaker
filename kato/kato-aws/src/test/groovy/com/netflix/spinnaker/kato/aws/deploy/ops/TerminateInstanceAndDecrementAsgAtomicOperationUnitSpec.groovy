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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult
import com.amazonaws.services.autoscaling.model.TerminateInstanceInAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.TerminateInstanceAndDecrementAsgDescription
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import spock.lang.Specification

class TerminateInstanceAndDecrementAsgAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Stub(Task))
  }

  void "operation invokes update to autoscaling group"() {
    setup:
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScaling(_, _, true) >> mockAutoScaling
    }
    def description = new TerminateInstanceAndDecrementAsgDescription(asgName: "myasg-stack-v000", region: "us-west-1", instance: "i-123456")
    description.credentials = TestCredential.named('baz')
    def operation = new TerminateInstanceAndDecrementAsgAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> { DescribeAutoScalingGroupsRequest request ->
      assert request.autoScalingGroupNames == ["myasg-stack-v000"]
      def asg = Stub(AutoScalingGroup) {
        getAutoScalingGroupName() >> "myasg-stack-v000"
        getMinSize() >> 1
        getDesiredCapacity() >> 2
      }
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
    }
    1 * mockAutoScaling.terminateInstanceInAutoScalingGroup(_) >> { TerminateInstanceInAutoScalingGroupRequest request ->
      assert request.instanceId == "i-123456"
      assert request.shouldDecrementDesiredCapacity
    }
    0 * _
  }

  void "operation deregisters instances from load balancers"() {
    setup:
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockLoadBalancing = Mock(AmazonElasticLoadBalancing)
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScaling(_, _, true) >> mockAutoScaling
      getAmazonElasticLoadBalancing(_, _, true) >> mockLoadBalancing
    }
    def description = new TerminateInstanceAndDecrementAsgDescription(asgName: "myasg-stack-v000", region: "us-west-1", instance: "i-123456")
    description.credentials = TestCredential.named('baz')
    def operation = new TerminateInstanceAndDecrementAsgAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> { DescribeAutoScalingGroupsRequest request ->
      assert request.autoScalingGroupNames == ["myasg-stack-v000"]
      def asg = Stub(AutoScalingGroup) {
        getAutoScalingGroupName() >> "myasg-stack-v000"
        getMinSize() >> 1
        getDesiredCapacity() >> 2
        getLoadBalancerNames() >> ['myasg--frontend']
      }
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
    }
    1 * mockLoadBalancing.deregisterInstancesFromLoadBalancer(_) >> { DeregisterInstancesFromLoadBalancerRequest request ->
      assert request.instances == [new Instance('i-123456')]
      assert request.loadBalancerName == 'myasg--frontend'
    }
    1 * mockAutoScaling.terminateInstanceInAutoScalingGroup(_) >> { TerminateInstanceInAutoScalingGroupRequest request ->
      assert request.instanceId == "i-123456"
      assert request.shouldDecrementDesiredCapacity
    }
    0 * _
  }

  void 'operation adjusts minSize if requested and required'() {
    setup:
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScaling(_, _, true) >> mockAutoScaling
    }
    def description = new TerminateInstanceAndDecrementAsgDescription(asgName: "myasg-stack-v000", region: "us-west-1", instance: "i-123456", adjustMinIfNecessary: true)
    description.credentials = TestCredential.named('baz')
    def operation = new TerminateInstanceAndDecrementAsgAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> { DescribeAutoScalingGroupsRequest request ->
      assert request.autoScalingGroupNames == ["myasg-stack-v000"]
      def asg = Stub(AutoScalingGroup) {
        getAutoScalingGroupName() >> "myasg-stack-v000"
        getMinSize() >> 1
        getDesiredCapacity() >> 1
      }
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
    }
    1 * mockAutoScaling.updateAutoScalingGroup(_) >> { UpdateAutoScalingGroupRequest request ->
      assert request.minSize == 0
    }
    1 * mockAutoScaling.terminateInstanceInAutoScalingGroup(_) >> { TerminateInstanceInAutoScalingGroupRequest request ->
      assert request.instanceId == "i-123456"
      assert request.shouldDecrementDesiredCapacity
    }
    0 * _
  }

  void 'operation fails if minSize adjustment needed but not requested'() {
    setup:
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScaling(_, _, true) >> mockAutoScaling
    }
    def description = new TerminateInstanceAndDecrementAsgDescription(asgName: "myasg-stack-v000", region: "us-west-1", instance: "i-123456")
    description.credentials = TestCredential.named('baz')
    def operation = new TerminateInstanceAndDecrementAsgAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    thrown(IllegalStateException)
    1 * mockAutoScaling.describeAutoScalingGroups(_) >> { DescribeAutoScalingGroupsRequest request ->
      assert request.autoScalingGroupNames == ["myasg-stack-v000"]
      def asg = Stub(AutoScalingGroup) {
        getAutoScalingGroupName() >> "myasg-stack-v000"
        getMinSize() >> 1
        getDesiredCapacity() >> 1
      }
      new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
    }
    0 * _
  }

  void 'operation adjusts maxSize to desired size after termination if requested'() {
    setup:
    def mockAutoScaling = Mock(AmazonAutoScaling)
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScaling(_, _, true) >> mockAutoScaling
    }
    def description = new TerminateInstanceAndDecrementAsgDescription(asgName: "myasg-stack-v000", region: "us-west-1", instance: "i-123456", setMaxToNewDesired: true)
    description.credentials = TestCredential.named('baz')
    def operation = new TerminateInstanceAndDecrementAsgAtomicOperation(description)
    operation.amazonClientProvider = mockAmazonClientProvider

    when:
    operation.operate([])

    then:
    2 * mockAutoScaling.describeAutoScalingGroups(_) >>
      { DescribeAutoScalingGroupsRequest request ->
        assert request.autoScalingGroupNames == ["myasg-stack-v000"]
        def asg = Stub(AutoScalingGroup) {
          getAutoScalingGroupName() >> "myasg-stack-v000"
          getMinSize() >> 1
          getDesiredCapacity() >> 2
          getMaxSize() >> 2
        }
        new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
      } >>
      { DescribeAutoScalingGroupsRequest request ->
          assert request.autoScalingGroupNames == ["myasg-stack-v000"]
          def asg = Stub(AutoScalingGroup) {
            getAutoScalingGroupName() >> "myasg-stack-v000"
            getMinSize() >> 1
            getDesiredCapacity() >> 1
            getMaxSize() >> 2
          }
          new DescribeAutoScalingGroupsResult().withAutoScalingGroups(asg)
      }
    1 * mockAutoScaling.terminateInstanceInAutoScalingGroup(_) >> { TerminateInstanceInAutoScalingGroupRequest request ->
      assert request.instanceId == "i-123456"
      assert request.shouldDecrementDesiredCapacity
    }
    1 * mockAutoScaling.updateAutoScalingGroup(_) >> { UpdateAutoScalingGroupRequest request ->
      assert request.maxSize == 1
    }
    0 * _

  }
}
