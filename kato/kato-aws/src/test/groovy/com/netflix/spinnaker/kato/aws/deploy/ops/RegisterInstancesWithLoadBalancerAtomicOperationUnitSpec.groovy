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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository

class RegisterInstancesWithLoadBalancerAtomicOperationUnitSpec extends InstanceLoadBalancerRegistrationUnitSpecSupport {
  def setupSpec() {
    description.credentials = TestCredential.named('test')
    description.instanceIds = ["i-123456"]
    op = new RegisterInstancesWithLoadBalancerAtomicOperation(description)
  }

  void 'should register instances with load balancers'() {
    setup:
    TaskRepository.threadLocalTask.set(Mock(Task) {
      _ * updateStatus(_,_)
    })

    def asg = Mock(AutoScalingGroup) {
      1 * getLoadBalancerNames() >> ["lb1"]
      1 * getInstances() >> [new Instance().withInstanceId("i-123456")]
      0 * _._
    }

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(description.asgName) >> asg
    1 * loadBalancing.registerInstancesWithLoadBalancer(_) >> { RegisterInstancesWithLoadBalancerRequest req ->
      assert req.instances*.instanceId == description.instanceIds
      assert req.loadBalancerName == "lb1"
    }
  }

  void 'should noop task if no load balancers found'() {
    setup:
    TaskRepository.threadLocalTask.set(Mock(Task) {
      _ * updateStatus(_,_)
      0 * fail()
    })

    def asg = Mock(AutoScalingGroup) {
      1 * getLoadBalancerNames() >> []
      1 * getInstances() >> description.instanceIds.collect { new Instance().withInstanceId(it) }
      0 * _._
    }

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(description.asgName) >> asg
    0 * loadBalancing.registerInstancesWithLoadBalancer(_)
  }

  void 'should use supplied load balancer names if no ASG specified'() {
    setup:
    description.asgName = null
    description.instanceIds = ['i-123456', 'i-234567']
    description.loadBalancerNames = ['elb-1', 'elb-2']

    when:
    op.operate([])

    then:
    0 * asgService.getAutoScalingGroup(_)
    2 * loadBalancing.registerInstancesWithLoadBalancer(_) >> { RegisterInstancesWithLoadBalancerRequest req ->
      assert req.instances*.instanceId == description.instanceIds
      assert description.loadBalancerNames.contains(req.loadBalancerName)
    }
  }

  void 'should noop task if no load balancer names supplied and no ASG specified'() {
    setup:
    description.asgName = null
    description.instanceIds = ['i-123456', 'i-234567']
    description.loadBalancerNames = null

    when:
    op.operate([])

    then:
    0 * asgService.getAutoScalingGroup(_)
    0 * loadBalancing.registerInstancesWithLoadBalancer(_)
  }
}
