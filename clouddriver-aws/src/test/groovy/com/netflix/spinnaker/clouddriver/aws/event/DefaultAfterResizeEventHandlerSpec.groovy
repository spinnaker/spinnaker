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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeLifecycleHooksRequest
import com.amazonaws.services.autoscaling.model.DescribeLifecycleHooksResult
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.autoscaling.model.LifecycleHook
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import spock.lang.Specification
import spock.lang.Subject

class DefaultAfterResizeEventHandlerSpec extends Specification {
  def task = Mock(Task)
  def amazonEC2 = Mock(AmazonEC2)
  def amazonAutoScaling = Mock(AmazonAutoScaling)

  def autoScalingGroup = new AutoScalingGroup().withAutoScalingGroupName("app-v001")
  def capacity = new ServerGroup.Capacity(0, 100, 0)

  def event = new AfterResizeEvent(
    task,
    amazonEC2,
    amazonAutoScaling,
    autoScalingGroup,
    capacity
  )

  @Subject
  def eventHandler = new DefaultAfterResizeEventHandler()

  def "should no-op if capacity > 0"() {
    given:
    capacity.desired = 1

    when:
    eventHandler.handle(event)

    then:
    0 * _
  }

  def "should no-op if load balancers present"() {
    given:
    autoScalingGroup.withLoadBalancerNames("my-loadbalancer")

    when:
    eventHandler.handle(event)

    then:
    1 * task.updateStatus("RESIZE", "Skipping explicit instance termination, server group is attached to one or more load balancers")
    0 * _
  }

  def "should no-op if terminating lifecycle hook present"() {
    when:
    eventHandler.handle(event)

    then:
    1 * amazonAutoScaling.describeLifecycleHooks(_) >> {
      return new DescribeLifecycleHooksResult()
        .withLifecycleHooks([new LifecycleHook().withLifecycleTransition("autoscaling:EC2_INSTANCE_TERMINATING")])
    }

    1 * task.updateStatus("RESIZE", "Skipping explicit instance termination, server group has one or more lifecycle hooks")
    0 * _
  }

  def "should explicitly terminate instances"() {
    given:
    autoScalingGroup.withInstances(new Instance().withInstanceId("i-12345678"))

    when:
    eventHandler.handle(event)

    then:
    1 * amazonAutoScaling.describeLifecycleHooks(_) >> { return new DescribeLifecycleHooksResult() }

    1 * task.updateStatus("RESIZE", "Terminating 1 of 1 instances in app-v001")
    1 * amazonEC2.terminateInstances({ TerminateInstancesRequest r -> r.instanceIds == ["i-12345678"] })
    0 * _
  }
}
