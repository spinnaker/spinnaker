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

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import com.netflix.spinnaker.kato.deploy.aws.description.EnableDisableAsgDescription
import com.netflix.spinnaker.kato.model.aws.AutoScalingProcessType
import com.netflix.spinnaker.kato.security.aws.DiscoveryAwareAmazonCredentials

class EnableAsgAtomicOperationUnitSpec extends EnableDisableAtomicOperationUnitSpecSupport {

  def setupSpec() {
    op = new EnableAsgAtomicOperation(description)
  }

  void 'should register instances from load balancers and resume scaling processes'() {
    setup:
    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "asg1"
    asg.getLoadBalancerNames() >> ["lb1"]
    asg.getInstances() >> [new Instance().withInstanceId("i1")]

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(_) >> asg
    1 * asgService.resumeProcesses(_, AutoScalingProcessType.getDisableProcesses())
    1 * loadBalancing.registerInstancesWithLoadBalancer(_) >> { RegisterInstancesWithLoadBalancerRequest req ->
      assert req.instances[0].instanceId == "i1"
      assert req.loadBalancerName == "lb1"
    }
  }

  void 'should enable instances for asg in discovery'() {
    setup:
    op.discoveryHostFormat = "http://us-west-1.discovery.ENV.netflix.net"
    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "asg1"
    asg.getInstances() >> [new Instance().withInstanceId("i1")]

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(_) >> asg
    1 * restTemplate.put("http://us-west-1.discovery.ENV.netflix.net/v2/apps/asg1/i1/status?value=UP", [:])
  }

    void 'should skip discovery if not enabled for account'() {
        setup:
        def noDiscovery =  new EnableDisableAsgDescription([
                asgName: "kato-main-v000",
                regions: ["us-west-1"],
                credentials: new DiscoveryAwareAmazonCredentials(Mock(AWSCredentials), "foo", "edda", false)
        ])

        def noDiscoveryOp = new EnableAsgAtomicOperation(noDiscovery)
        wireOpMocks(noDiscoveryOp)

        noDiscoveryOp.discoveryHostFormat = "http://us-west-1.discovery.ENV.netflix.net"
        def asg = Mock(AutoScalingGroup)
        asg.getAutoScalingGroupName() >> "asg1"
        asg.getInstances() >> [new Instance().withInstanceId("i1")]

        when:
        noDiscoveryOp.operate([])

        then:
        1 * asgService.getAutoScalingGroup(_) >> asg
        0 * restTemplate.put(_, [:])
    }

}
