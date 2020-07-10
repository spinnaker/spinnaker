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
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableAsgDescription
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport

class EnableAsgAtomicOperationUnitSpec extends EnableDisableAtomicOperationUnitSpecSupport {

  def setupSpec() {
    def cred = TestCredential.named('test', [discovery: 'http://{{region}}.discovery.netflix.net'])
    description.credentials = cred
    op = new EnableAsgAtomicOperation(description)

  }

  def 'should register instances from load balancer and resume scaling processes'() {
    given:
    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "asg1"
    asg.getLoadBalancerNames() >> ["lb1"]
    asg.getInstances() >> [new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("i1").withLifecycleState("InService") ]

    and:
    def instance = new Instance().withState(new InstanceState().withName("running")).withInstanceId("i1")
    def describeInstanceResult = Mock(DescribeInstancesResult)
    describeInstanceResult.getReservations() >> [new Reservation().withInstances(instance)]

    when:
    op.operate([])

    then:
    1 * amazonEc2.describeInstances(_) >> describeInstanceResult
    1 * asgService.getAutoScalingGroup(_) >> asg
    1 * asgService.resumeProcesses(_, AutoScalingProcessType.getDisableProcesses())
    1 * loadBalancing.registerInstancesWithLoadBalancer(_) >> { RegisterInstancesWithLoadBalancerRequest req ->
      assert req.instances[0].instanceId == "i1"
      assert req.loadBalancerName == "lb1"
    }
  }

  def 'should enable instances for asg in discovery'() {
    given:
    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "asg1"
    asg.getInstances() >> [new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("i1").withLifecycleState("InService") ]

    and:
    def instance1 = new Instance().withState(new InstanceState().withName("running")).withInstanceId("i1")
    def instance2 = new Instance().withState(new InstanceState().withName("terminated")).withInstanceId("i2")
    def describeInstanceResult = Mock(DescribeInstancesResult)
    describeInstanceResult.getReservations() >> [new Reservation().withInstances(instance1), new Reservation().withInstances(instance2)]

    when:
    op.operate([])

    then:
    1 * amazonEc2.describeInstances(_) >> describeInstanceResult
    2 * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    1 * asgService.getAutoScalingGroup(_) >> asg
    1 * eureka.getInstanceInfo('i1') >>
        [
            instance: [
                app: "asg1",
                status: "OUT_OF_SERVICE"
            ]
        ]
    1 * eureka.resetInstanceStatus('asg1', 'i1', AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value)
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

    def noDiscoveryOp = new EnableAsgAtomicOperation(noDiscovery)
    wireOpMocks(noDiscoveryOp)

    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "asg1"
    asg.getInstances() >> [new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("i1").withLifecycleState("InService") ]

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
