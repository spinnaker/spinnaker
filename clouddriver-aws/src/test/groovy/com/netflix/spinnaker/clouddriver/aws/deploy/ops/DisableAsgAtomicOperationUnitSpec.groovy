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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceState
import com.amazonaws.services.ec2.model.Reservation
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableAsgDescription
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import retrofit.client.Response

class DisableAsgAtomicOperationUnitSpec extends EnableDisableAtomicOperationUnitSpecSupport {

  void setupSpec() {
    def cred = TestCredential.named('test', [discovery: 'http://{{region}}.discovery.netflix.net'])
    description.credentials = cred
    op = new DisableAsgAtomicOperation(description)
  }

  def 'should deregister instances from load balancer and suspend scaling processes'() {
    given:
    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "asg1"
    asg.getLoadBalancerNames() >> ["lb1"]

    and:
    def instance = new Instance().withState(new InstanceState().withName("running")).withInstanceId("i1")
    def describeInstanceResult = Mock(DescribeInstancesResult)
    describeInstanceResult.getReservations() >> [new Reservation().withInstances(instance)]

    when:
    op.operate([])

    then:
    1 * amazonEc2.describeInstances(_) >> describeInstanceResult
    1 * asgService.getAutoScalingGroup(_) >> asg
    1 * asgService.suspendProcesses(_, AutoScalingProcessType.getDisableProcesses())
    1 * loadBalancing.deregisterInstancesFromLoadBalancer(_) >> { DeregisterInstancesFromLoadBalancerRequest req ->
      assert req.instances[0].instanceId == "i1"
      assert req.loadBalancerName == "lb1"
    }
  }

  def 'should not fail if a load balancer does not exist'() {
    given:
    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "asg1"
    asg.getLoadBalancerNames() >> ["lb1"]

    and:
    def instance = new Instance().withState(new InstanceState().withName("running")).withInstanceId("i1")
    def describeInstanceResult = Mock(DescribeInstancesResult)
    describeInstanceResult.getReservations() >> [new Reservation().withInstances(instance)]

    when:
    op.operate([])

    then:
    1 * amazonEc2.describeInstances(_) >> describeInstanceResult
    1 * asgService.getAutoScalingGroup(_) >> asg
    1 * asgService.suspendProcesses(_, AutoScalingProcessType.getDisableProcesses())
    1 * loadBalancing.deregisterInstancesFromLoadBalancer(_) >> { throw new LoadBalancerNotFoundException("Does not exist") }
    1 * eureka.getInstanceInfo('i1') >>
      [
        instance: [
          app: "asg1"
        ]
      ]
    1 * eureka.updateInstanceStatus('asg1', 'i1', 'OUT_OF_SERVICE') >> new Response('http://foo', 200, 'OK', [], null)
    2 * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    0 * task.fail()
  }

  def 'should disable instances for asg in discovery'() {
    given:
    def asg = Mock(AutoScalingGroup)
    def instance = new Instance().withState(new InstanceState().withName("running")).withInstanceId("i1")
    def describeInstanceResult = Mock(DescribeInstancesResult)
    describeInstanceResult.getReservations() >> [new Reservation().withInstances(instance)]

    when:
    op.operate([])

    then:
    1 * amazonEc2.describeInstances(_) >> describeInstanceResult
    2 * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    1 * asgService.getAutoScalingGroup(_) >> asg
    1 * eureka.getInstanceInfo('i1') >>
        [
            instance: [
                app: "asg1"
            ]
        ]
    1 * eureka.updateInstanceStatus('asg1', 'i1', 'OUT_OF_SERVICE')
  }

  def 'should skip discovery if not enabled for account'() {
    given:
    def noDiscovery = new EnableDisableAsgDescription([
      asgs: [[
        serverGroupName: "kato-main-v000",
        region         : "us-west-1"
      ]],
      credentials: TestCredential.named('foo')
    ])

    def noDiscoveryOp = new DisableAsgAtomicOperation(noDiscovery)
    wireOpMocks(noDiscoveryOp)

    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "asg1"

    and:
    def instance = new Instance().withState(new InstanceState().withName("running")).withInstanceId("i1")
    def describeInstanceResult = Mock(DescribeInstancesResult)
    describeInstanceResult.getReservations() >> [new Reservation().withInstances(instance)]

    when:
    noDiscoveryOp.operate([])

    then:
    1 * amazonEc2.describeInstances(_) >> describeInstanceResult
    1 * asgService.getAutoScalingGroup(_) >> asg
    0 * eureka.updateInstanceStatus(*_)
  }

}
