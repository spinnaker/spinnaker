/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.event

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.autoscaling.model.DescribeLifecycleHooksRequest
import software.amazon.awssdk.services.autoscaling.model.DescribeLifecycleHooksResponse
import software.amazon.awssdk.services.autoscaling.model.Instance
import software.amazon.awssdk.services.autoscaling.model.LifecycleHook
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DefaultAfterResizeEventHandlerSpec extends Specification {
  def task = Mock(Task)
  def amazonEC2 = Mock(Ec2Client)
  def amazonAutoScaling = Mock(AutoScalingClient)

  def autoScalingGroup = AutoScalingGroup.builder().autoScalingGroupName("app-v001").build()
  def capacity = new ServerGroup.Capacity(0, 100, 0)

  def event() {
    new AfterResizeEvent(
      task,
      amazonEC2,
      amazonAutoScaling,
      autoScalingGroup,
      capacity
    )
  }

  @Subject
  def eventHandler = new DefaultAfterResizeEventHandler()

  @Unroll
  def "should no-op if desired capacity > 0 or not specified "() {
    given:
    capacity.desired = desiredCapacity

    when:
    eventHandler.handle(event())

    then:
    0 * _

    where:
    desiredCapacity << [1, null]
  }

  def "should no-op if load balancers present"() {
    given:
    autoScalingGroup = autoScalingGroup.toBuilder().loadBalancerNames(["my-loadbalancer"]).build()

    when:
    eventHandler.handle(event())

    then:
    1 * task.updateStatus("RESIZE", "Skipping explicit instance termination, server group is attached to one or more load balancers")
    0 * _
  }

  def "should no-op if terminating lifecycle hook present"() {
    when:
    eventHandler.handle(event())

    then:
    1 * amazonAutoScaling.describeLifecycleHooks(_) >> {
      return DescribeLifecycleHooksResponse.builder()
        .lifecycleHooks([LifecycleHook.builder().lifecycleTransition("autoscaling:EC2_INSTANCE_TERMINATING").build()])
        .build()
    }

    1 * task.updateStatus("RESIZE", "Skipping explicit instance termination, server group has one or more lifecycle hooks")
    0 * _
  }

  def "should explicitly terminate instances"() {
    given:
    autoScalingGroup = autoScalingGroup.toBuilder().instances([Instance.builder().instanceId("i-12345678").build()]).build()

    when:
    eventHandler.handle(event())

    then:
    1 * amazonAutoScaling.describeLifecycleHooks(_) >> { return DescribeLifecycleHooksResponse.builder().build() }

    1 * task.updateStatus("RESIZE", "Terminating 1 of 1 instances in app-v001")
    1 * amazonEC2.terminateInstances({ TerminateInstancesRequest r -> r.instanceIds == ["i-12345678"] })
    0 * _
  }
}
