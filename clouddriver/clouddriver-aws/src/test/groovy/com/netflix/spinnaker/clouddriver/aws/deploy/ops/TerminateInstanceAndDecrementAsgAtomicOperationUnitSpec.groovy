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

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse
import software.amazon.awssdk.services.autoscaling.model.TerminateInstanceInAutoScalingGroupRequest
import software.amazon.awssdk.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.Instance
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.TerminateInstanceAndDecrementAsgDescription
import spock.lang.Specification

class TerminateInstanceAndDecrementAsgAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Stub(Task))
  }

  void "operation invokes update to autoscaling group"() {
    setup:
    def mockAutoScaling = Mock(AutoScalingClient)
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScalingV2(_, _) >> mockAutoScaling
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
      def asg = AutoScalingGroup.builder()
        .autoScalingGroupName("myasg-stack-v000")
        .minSize(1)
        .desiredCapacity(2)
        .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId(description.instance).build()])
        .build()
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asg).build()
    }
    1 * mockAutoScaling.terminateInstanceInAutoScalingGroup(_) >> { TerminateInstanceInAutoScalingGroupRequest request ->
      assert request.instanceId == "i-123456"
      assert request.shouldDecrementDesiredCapacity
    }
    0 * _
  }

  void "operation deregisters instances from load balancers"() {
    setup:
    def mockAutoScaling = Mock(AutoScalingClient)
    def mockLoadBalancing = Mock(AmazonElasticLoadBalancing)
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScalingV2(_, _) >> mockAutoScaling
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
      def asg = AutoScalingGroup.builder()
        .autoScalingGroupName("myasg-stack-v000")
        .minSize(1)
        .desiredCapacity(2)
        .loadBalancerNames(['myasg--frontend'])
        .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId(description.instance).build()])
        .build()
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asg).build()
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
    def mockAutoScaling = Mock(AutoScalingClient)
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScalingV2(_, _) >> mockAutoScaling
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
      def asg = AutoScalingGroup.builder()
        .autoScalingGroupName("myasg-stack-v000")
        .minSize(1)
        .desiredCapacity(1)
        .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId(description.instance).build()])
        .build()
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asg).build()
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
    def mockAutoScaling = Mock(AutoScalingClient)
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScalingV2(_, _) >> mockAutoScaling
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
      def asg = AutoScalingGroup.builder()
        .autoScalingGroupName("myasg-stack-v000")
        .minSize(1)
        .desiredCapacity(1)
        .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId(description.instance).build()])
        .build()
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asg).build()
    }
    0 * _
  }

  void 'operation adjusts maxSize to desired size after termination if requested'() {
    setup:
    def mockAutoScaling = Mock(AutoScalingClient)
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScalingV2(_, _) >> mockAutoScaling
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
        def asg = AutoScalingGroup.builder()
          .autoScalingGroupName("myasg-stack-v000")
          .minSize(1)
          .desiredCapacity(2)
          .maxSize(2)
          .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId(description.instance).build()])
          .build()
        DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asg).build()
      } >>
      { DescribeAutoScalingGroupsRequest request ->
          assert request.autoScalingGroupNames == ["myasg-stack-v000"]
          def asg = AutoScalingGroup.builder()
            .autoScalingGroupName("myasg-stack-v000")
            .minSize(1)
            .desiredCapacity(1)
            .maxSize(2)
            .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId(description.instance).build()])
            .build()
          DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(asg).build()
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

  void "should fail operation if the instance isn't part of the Server Group"() {
    given:
    def amazonAutoScaling = Mock(AutoScalingClient)
    def amazonClientProvider = Stub(AmazonClientProvider) {
      getAutoScalingV2(_, _) >> amazonAutoScaling
    }

    def description = new TerminateInstanceAndDecrementAsgDescription(
      asgName: "test-v001",
      region: "us-east-1",
      instance: "i-123",
      setMaxToNewDesired: true
    )

    def serverGroup = AutoScalingGroup.builder()
      .autoScalingGroupName(description.asgName)
      .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId("i-456").build()])
      .desiredCapacity(3)
      .minSize(1)
      .build()

    and:
    1 * amazonAutoScaling.describeAutoScalingGroups(_) >>
      DescribeAutoScalingGroupsResponse.builder().autoScalingGroups(serverGroup).build()

    def operation = new TerminateInstanceAndDecrementAsgAtomicOperation(description)
    operation.amazonClientProvider = amazonClientProvider

    when:
    operation.operate([])

    then:
    thrown(IllegalArgumentException)
  }
}
