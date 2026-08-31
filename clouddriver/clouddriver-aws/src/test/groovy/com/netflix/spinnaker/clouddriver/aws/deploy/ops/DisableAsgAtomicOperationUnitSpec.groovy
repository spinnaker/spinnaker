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

import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse
import software.amazon.awssdk.services.ec2.model.Instance
import software.amazon.awssdk.services.ec2.model.InstanceState
import software.amazon.awssdk.services.ec2.model.InstanceStateName
import software.amazon.awssdk.services.ec2.model.Reservation
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableAsgDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery.AwsEurekaSupport
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Unroll

class DisableAsgAtomicOperationUnitSpec extends EnableDisableAtomicOperationUnitSpecSupport {

  void setupSpec() {
    def cred = TestCredential.named('test', [discovery: 'http://{{region}}.discovery.netflix.net'])
    description.credentials = cred
    op = new DisableAsgAtomicOperation(description)
  }

  def 'should deregister instances from load balancer and suspend scaling processes'() {
    given:
    def asg = AutoScalingGroup.builder()
      .autoScalingGroupName("asg1")
      .loadBalancerNames(["lb1"])
      .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId("i1").lifecycleState("InService").build()])
      .build()

    and:
    def instance = Instance.builder().state(InstanceState.builder().name(InstanceStateName.RUNNING).build()).instanceId("i1").build()
    def describeInstanceResult = DescribeInstancesResponse.builder().reservations([Reservation.builder().instances(instance).build()]).build()

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
    def asg = AutoScalingGroup.builder()
      .autoScalingGroupName("asg1")
      .loadBalancerNames(["lb1"])
      .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId("i1").lifecycleState("InService").build()])
      .build()

    and:
    def instance = Instance.builder().state(InstanceState.builder().name(InstanceStateName.RUNNING).build()).instanceId("i1").build()
    def describeInstanceResult = DescribeInstancesResponse.builder().reservations([Reservation.builder().instances(instance).build()]).build()

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
      Calls.response([
        instance: [
          app: "asg1"
        ]
      ])
    1 * eureka.updateInstanceStatus('asg1', 'i1', 'OUT_OF_SERVICE') >> Calls.response(null)
    2 * task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    0 * task.fail()
  }

  def 'should disable instances for asg in discovery'() {
    given:
    def asg = AutoScalingGroup.builder()
      .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId("i1").lifecycleState("InService").build()])
      .build()
    def instance = Instance.builder().state(InstanceState.builder().name(InstanceStateName.RUNNING).build()).instanceId("i1").build()
    def describeInstanceResult = DescribeInstancesResponse.builder().reservations([Reservation.builder().instances(instance).build()]).build()

    when:
    op.operate([])

    then:
    1 * amazonEc2.describeInstances(_) >> describeInstanceResult
    2 * task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    1 * asgService.getAutoScalingGroup(_) >> asg
    1 * eureka.getInstanceInfo('i1') >>
      Calls.response([
        instance: [
          app: "asg1"
        ]
      ])
    1 * eureka.updateInstanceStatus('asg1', 'i1', 'OUT_OF_SERVICE') >> Calls.response(null)
  }

  def 'should not fail because of discovery errors on disable'() {
    given:
    def asg = AutoScalingGroup.builder()
      .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId("i1").lifecycleState("InService").build()])
      .build()
    def instance = Instance.builder().state(InstanceState.builder().name(InstanceStateName.RUNNING).build()).instanceId("i1").build()
    def describeInstanceResult = DescribeInstancesResponse.builder().reservations([Reservation.builder().instances(instance).build()]).build()

    eureka.updateInstanceStatus('asg1', 'i1', 'OUT_OF_SERVICE') >> {
      throw makeSpinnakerHttpException(503)
    }

    when:
    op.operate([])

    then:
    _ * amazonEc2.describeInstances(_) >> describeInstanceResult
    _ * task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    _ * asgService.getAutoScalingGroup(_) >> asg
    _ * eureka.getInstanceInfo('i1') >>
      Calls.response([
        instance: [
          app: "asg1"
        ]
      ])
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

    def asg = AutoScalingGroup.builder()
      .autoScalingGroupName("asg1")
      .instances([software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId("i1").lifecycleState("InService").build()])
      .build()

    and:
    def instance = Instance.builder().state(InstanceState.builder().name(InstanceStateName.RUNNING).build()).instanceId("i1").build()
    def describeInstanceResult = DescribeInstancesResponse.builder().reservations([Reservation.builder().instances(instance).build()]).build()

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
    description.desiredPercentage = percentage

    def runningState = InstanceState.builder().name(InstanceStateName.RUNNING).code(16).build()

    def asg = AutoScalingGroup.builder().instances([
      software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId('00001').lifecycleState('InService').build(),
      software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId('00002').lifecycleState('InService').build(),
      software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId('00003').lifecycleState('InService').build(),
      software.amazon.awssdk.services.autoscaling.model.Instance.builder().instanceId('00004').lifecycleState('InService').build(),
    ]).build()

    def describeInstancesResult = DescribeInstancesResponse.builder().reservations([
      Reservation.builder().instances([
        Instance.builder().instanceId('00001').state(runningState).build(),
        Instance.builder().instanceId('00002').state(runningState).build(),
        Instance.builder().instanceId('00003').state(runningState).build(),
        Instance.builder().instanceId('00004').state(runningState).build()
      ]).build()
    ]).build()

    and:
    asgService.getAutoScalingGroup(_) >> asg
    1 * amazonEc2.describeInstances(_) >> describeInstancesResult
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
    def asg = AutoScalingGroup.builder().build()
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

  private static SpinnakerHttpException makeSpinnakerHttpException(int status) {
    String url = "https://some-url";
    retrofit2.Response retrofit2Response =
      retrofit2.Response.error(
        status,
        ResponseBody.create(
          MediaType.parse("application/json"), "{ \"message\": \"arbitrary message\" }"));

    Retrofit retrofit =
      new Retrofit.Builder()
        .baseUrl(url)
        .addConverterFactory(JacksonConverterFactory.create())
        .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }

}
