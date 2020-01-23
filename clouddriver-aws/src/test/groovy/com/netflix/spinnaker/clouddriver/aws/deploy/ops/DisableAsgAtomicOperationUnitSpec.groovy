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
import com.amazonaws.services.ec2.model.InstanceStateName
import com.amazonaws.services.ec2.model.Reservation
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableAsgDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery.AwsEurekaSupport
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import com.netflix.spinnaker.clouddriver.eureka.model.EurekaApplication
import com.netflix.spinnaker.clouddriver.eureka.model.EurekaInstance
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Unroll

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
    asg.getInstances() >> [new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("i1").withLifecycleState("InService")]

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
    asg.getInstances() >> [new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("i1").withLifecycleState("InService")]

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
    1 * loadBalancing.deregisterInstancesFromLoadBalancer(_) >> {
      throw new LoadBalancerNotFoundException("Does not exist")
    }
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
    asg.getInstances() >> [new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("i1").withLifecycleState("InService")]
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

  def 'should not fail because of discovery errors on disable'() {
    given:
    def asg = Mock(AutoScalingGroup)
    asg.getInstances() >> [new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("i1").withLifecycleState("InService")]
    def instance = new Instance().withState(new InstanceState().withName("running")).withInstanceId("i1")
    def describeInstanceResult = Mock(DescribeInstancesResult)
    describeInstanceResult.getReservations() >> [new Reservation().withInstances(instance)]

    eureka.updateInstanceStatus('asg1', 'i1', 'OUT_OF_SERVICE') >> {
      throw new RetrofitError("error", "url",
        new Response("url", 503, "service unavailable", [], null),
        null, null, null, null)
    }

    when:
    op.operate([])

    then:
    _ * amazonEc2.describeInstances(_) >> describeInstanceResult
    _ * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    _ * asgService.getAutoScalingGroup(_) >> asg
    _ * eureka.getInstanceInfo('i1') >>
      [
        instance: [
          app: "asg1"
        ]
      ]
    0 * task.fail()
  }

  def 'should skip discovery if not enabled for account'() {
    given:
    def noDiscovery = new EnableDisableAsgDescription([
      asgs       : [[
                      serverGroupName: "kato-main-v000",
                      region         : "us-west-1"
                    ]],
      credentials: TestCredential.named('foo')
    ])

    def noDiscoveryOp = new DisableAsgAtomicOperation(noDiscovery)
    wireOpMocks(noDiscoveryOp)

    def asg = Mock(AutoScalingGroup)
    asg.getAutoScalingGroupName() >> "asg1"
    asg.getInstances() >> [new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("i1").withLifecycleState("InService")]

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

  @Unroll("Should disable #instancesAffected instances when #percentage percentage is requested")
  void 'should filter down to a list of instance ids by percentage'() {
    setup:
    def asg = Mock(AutoScalingGroup)
    description.desiredPercentage = percentage

    def reservation = Mock(Reservation)
    def describeInstancesResult = Mock(DescribeInstancesResult)
    describeInstancesResult.nextToken = null
    def runningState = new InstanceState().withName(InstanceStateName.Running).withCode(16)

    and:
    asgService.getAutoScalingGroup(_) >> asg
    asg.getInstances() >> [
      new com.amazonaws.services.autoscaling.model.Instance(instanceId: '00001', lifecycleState: 'InService'),
      new com.amazonaws.services.autoscaling.model.Instance(instanceId: '00002', lifecycleState: 'InService'),
      new com.amazonaws.services.autoscaling.model.Instance(instanceId: '00003', lifecycleState: 'InService'),
      new com.amazonaws.services.autoscaling.model.Instance(instanceId: '00004', lifecycleState: 'InService'),
    ]
    1 * amazonEc2.describeInstances(_) >> describeInstancesResult
    1 * describeInstancesResult.getReservations() >> [reservation]
    1 * reservation.getInstances() >> [
      new com.amazonaws.services.ec2.model.Instance(instanceId: '00001', state: runningState),
      new com.amazonaws.services.ec2.model.Instance(instanceId: '00002', state: runningState),
      new com.amazonaws.services.ec2.model.Instance(instanceId: '00003', state: runningState),
      new com.amazonaws.services.ec2.model.Instance(instanceId: '00004', state: runningState)
    ]
    op.discoverySupport = Mock(AwsEurekaSupport)
    op.discoverySupport.getInstanceToModify(_, _, _, _, percentage) >> instances

    when:
    op.operate([])

    then:
    1 * op.discoverySupport.updateDiscoveryStatusForInstances(_, _, _, _, { it.size() == instancesAffected })

    where:
    percentage | instances          || instancesAffected
    75         | ['00001']          || 1
    100        | ['00001', '00004'] || 2
    null       | null               || 4
  }

  @Unroll("Should invoke suspend process #invocations times when desiredPercentage is #desiredPercentage")
  void 'should suspend processes only if desired percentage is null or 100'() {
    given:
    def asg = Mock(AutoScalingGroup)
    description.desiredPercentage = desiredPercentage

    when:
    op.operate([])

    then:
    1 * asgService.getAutoScalingGroup(_) >> asg
    invocations * asgService.suspendProcesses(_, AutoScalingProcessType.getDisableProcesses())

    where:
    desiredPercentage || invocations
    null              || 1
    100               || 1
    0                 || 0
    50                || 0
    99                || 0
  }

}
