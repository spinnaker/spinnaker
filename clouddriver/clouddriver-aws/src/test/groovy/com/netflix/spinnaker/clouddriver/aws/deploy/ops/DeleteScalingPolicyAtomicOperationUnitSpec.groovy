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
import software.amazon.awssdk.services.autoscaling.model.Alarm
import software.amazon.awssdk.services.autoscaling.model.DeletePolicyRequest
import software.amazon.awssdk.services.autoscaling.model.DescribePoliciesRequest
import software.amazon.awssdk.services.autoscaling.model.DescribePoliciesResponse
import software.amazon.awssdk.services.autoscaling.model.ScalingPolicy
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.DeleteAlarmsRequest
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DeleteScalingPolicyAtomicOperationUnitSpec extends Specification {
  private static final String ACCOUNT = "test"

  def credz = Stub(NetflixAmazonCredentials) {
    getName() >> ACCOUNT
  }

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def description = new DeleteScalingPolicyDescription(
    serverGroupName: "kato-main-v000",
    policyName: "scalingPolicy1",
    region: "us-west-1",
    credentials: credz
  )

  def autoScaling = Mock(AutoScalingClient)
  def cloudWatch = Mock(CloudWatchClient)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAutoScalingV2(credz, "us-west-1") >> autoScaling
    getAmazonCloudWatchV2(credz, "us-west-1") >> cloudWatch
  }

  @Subject def op = new DeleteScalingPolicyAtomicOperation(description)

  def setup() {
    op.amazonClientProvider = amazonClientProvider
  }

  void "delete scaling policy"() {

    when:
    op.operate([])

    then:
    1 * autoScaling.deletePolicy(DeletePolicyRequest.builder()
      .policyName("scalingPolicy1")
      .autoScalingGroupName("kato-main-v000")
      .build())
    1 * autoScaling.describePolicies(DescribePoliciesRequest.builder()
        .policyNames(description.policyName)
        .autoScalingGroupName(description.serverGroupName)
        .build()) >> DescribePoliciesResponse.builder().build()
  }

  @Unroll
  void "deletes alarm if no actions or just the policy we deleted are assigned to it"() {

    given:
    def alarm = Alarm.builder().alarmARN("alarm:arn").alarmName("the-alarm").build()
    def policy = ScalingPolicy.builder().alarms(alarm).policyARN("policy:arn").build()
    def policyResponse = DescribePoliciesResponse.builder().scalingPolicies(policy).build()
    def metricAlarm = MetricAlarm.builder().alarmActions(arns).build()
    def alarmsResponse = DescribeAlarmsResponse.builder().metricAlarms(metricAlarm).build()

    when:
    op.operate([])

    then:
    1 * autoScaling.deletePolicy(DeletePolicyRequest.builder()
        .policyName("scalingPolicy1")
        .autoScalingGroupName("kato-main-v000")
        .build())
    1 * autoScaling.describePolicies(DescribePoliciesRequest.builder()
        .policyNames(description.policyName)
        .autoScalingGroupName(description.serverGroupName)
        .build()) >> policyResponse
    1 * cloudWatch.describeAlarms(DescribeAlarmsRequest.builder().alarmNames("the-alarm").build()) >> alarmsResponse
    1 * cloudWatch.deleteAlarms(DeleteAlarmsRequest.builder().alarmNames("the-alarm").build())
    0 * _

    where:
    arns << [ [], ["policy:arn"]]
  }

  @Unroll
  void "does not delete the alarm if other actions are assigned to it"() {

    given:
    def alarm = Alarm.builder().alarmARN("alarm:arn").alarmName("the-alarm").build()
    def policy = ScalingPolicy.builder().alarms(alarm).policyARN("policy:arn").build()
    def policyResponse = DescribePoliciesResponse.builder().scalingPolicies(policy).build()
    def metricAlarm = MetricAlarm.builder().alarmActions(arns).build()
    def alarmsResponse = DescribeAlarmsResponse.builder().metricAlarms(metricAlarm).build()

    when:
    op.operate([])

    then:
    1 * autoScaling.deletePolicy(DeletePolicyRequest.builder()
        .policyName("scalingPolicy1")
        .autoScalingGroupName("kato-main-v000")
        .build())
    1 * autoScaling.describePolicies(DescribePoliciesRequest.builder()
        .policyNames(description.policyName)
        .autoScalingGroupName(description.serverGroupName)
        .build()) >> policyResponse
    1 * cloudWatch.describeAlarms(DescribeAlarmsRequest.builder().alarmNames("the-alarm").build()) >> alarmsResponse
    0 * _

    where:
    arns << [ ["policy:arn", "otherpolicy:arn"], ["otherpolicy:arn"]]
  }

}
