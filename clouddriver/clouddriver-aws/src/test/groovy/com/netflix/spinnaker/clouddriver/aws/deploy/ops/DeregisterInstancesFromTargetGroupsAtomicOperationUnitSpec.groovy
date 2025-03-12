/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.elasticloadbalancingv2.model.DeregisterTargetsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository

class DeregisterInstancesFromTargetGroupsAtomicOperationUnitSpec extends InstanceTargetGroupRegistrationUnitSpecSupport {
  def setupSpec() {
    description.credentials = TestCredential.named('test')
    description.instanceIds = ["i-123456"]
    op = new DeregisterInstancesFromTargetGroupAtomicOperation(description) {
      @Override TargetGroupLookupHelper lookupHelper() {
        return new TargetGroupLookupHelper()
      }
    }
  }

  def setup() {
    TaskRepository.threadLocalTask.set(Mock(Task) {
      _ * updateStatus(_,_)
    })
  }

  void 'should deregister instances from target groups'() {
    setup:
    def asg = Mock(AutoScalingGroup) {
      1 * getTargetGroupARNs() >> ["tg1"]
      1 * getInstances() >> [new Instance().withInstanceId("i-123456")]
      0 * _._
    }

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(description.asgName) >> asg
    1 * loadBalancingV2.deregisterTargets(_) >> { DeregisterTargetsRequest req ->
      assert req.targets*.id == description.instanceIds
      assert req.targetGroupArn == "tg1"
    }
  }

  void 'should noop task if no load balancers found'() {
    setup:
    def asg = Mock(AutoScalingGroup) {
      1 * getTargetGroupARNs() >> []
      1 * getInstances() >> description.instanceIds.collect { new Instance().withInstanceId(it) }
      0 * _._
    }

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(description.asgName) >> asg
    0 * loadBalancingV2.deregisterTargets(_)
  }

  void 'should use supplied targetGroupNames if no ASG specified'() {
    setup:
    description.asgName = null
    description.instanceIds = ['i-123456', 'i-234567']
    description.targetGroupNames = ['tg-1', 'tg-2']

    when:
    op.operate([])

    then:
    0 * asgService.getAutoScalingGroup(_)
    2 * loadBalancingV2.describeTargetGroups(_) >> { DescribeTargetGroupsRequest req -> new DescribeTargetGroupsResult().withTargetGroups(new TargetGroup().withTargetGroupName(req.getNames()[0]).withTargetGroupArn(req.getNames()[0]))}
    2 * loadBalancingV2.deregisterTargets(_) >> { DeregisterTargetsRequest req ->
      assert req.targets*.id == description.instanceIds
      assert description.targetGroupNames.contains(req.targetGroupArn)
    }
  }

  void 'should noop task if no target group names supplied and no ASG specified'() {
    setup:
    description.asgName = null
    description.instanceIds = ['i-123456', 'i-234567']
    description.targetGroupNames = null

    when:
    op.operate([])

    then:
    0 * asgService.getAutoScalingGroup(_)
    0 * loadBalancingV2.deregisterTargets(_)
  }
}
