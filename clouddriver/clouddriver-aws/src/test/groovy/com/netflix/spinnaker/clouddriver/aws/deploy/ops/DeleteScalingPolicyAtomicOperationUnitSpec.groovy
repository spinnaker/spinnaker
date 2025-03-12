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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.Alarm
import com.amazonaws.services.autoscaling.model.DeletePolicyRequest
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult
import com.amazonaws.services.autoscaling.model.ScalingPolicy
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult
import com.amazonaws.services.cloudwatch.model.MetricAlarm
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

  def autoScaling = Mock(AmazonAutoScaling)
  def cloudWatch = Mock(AmazonCloudWatch)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAutoScaling(credz, "us-west-1", true) >> autoScaling
    getCloudWatch(credz, "us-west-1", true) >> cloudWatch
  }

  @Subject def op = new DeleteScalingPolicyAtomicOperation(description)

  def setup() {
    op.amazonClientProvider = amazonClientProvider
  }

  void "delete scaling policy"() {

    when:
    op.operate([])

    then:
    1 * autoScaling.deletePolicy(new DeletePolicyRequest(
      policyName: "scalingPolicy1",
      autoScalingGroupName: "kato-main-v000"
    ))
    1 * autoScaling.describePolicies(new DescribePoliciesRequest()
        .withPolicyNames(description.policyName)
        .withAutoScalingGroupName(description.serverGroupName)) >> new DescribePoliciesResult()
  }

  @Unroll
  void "deletes alarm if no actions or just the policy we deleted are assigned to it"() {

    given:
    def alarm = new Alarm().withAlarmARN("alarm:arn").withAlarmName("the-alarm")
    def policy = new ScalingPolicy().withAlarms(alarm).withPolicyARN("policy:arn")
    def policyResponse = new DescribePoliciesResult().withScalingPolicies(policy)
    def metricAlarm = new MetricAlarm().withAlarmActions(arns)
    def alarmsResponse = new DescribeAlarmsResult().withMetricAlarms(metricAlarm)

    when:
    op.operate([])

    then:
    1 * autoScaling.deletePolicy(new DeletePolicyRequest(
        policyName: "scalingPolicy1",
        autoScalingGroupName: "kato-main-v000"
    ))
    1 * autoScaling.describePolicies(new DescribePoliciesRequest()
        .withPolicyNames(description.policyName)
        .withAutoScalingGroupName(description.serverGroupName)) >> policyResponse
    1 * cloudWatch.describeAlarms(new DescribeAlarmsRequest().withAlarmNames("the-alarm")) >> alarmsResponse
    1 * cloudWatch.deleteAlarms(new DeleteAlarmsRequest().withAlarmNames("the-alarm"))
    0 * _

    where:
    arns << [ [], ["policy:arn"]]
  }

  @Unroll
  void "does not delete the alarm if other actions are assigned to it"() {

    given:
    def alarm = new Alarm().withAlarmARN("alarm:arn").withAlarmName("the-alarm")
    def policy = new ScalingPolicy().withAlarms(alarm).withPolicyARN("policy:arn")
    def policyResponse = new DescribePoliciesResult().withScalingPolicies(policy)
    def metricAlarm = new MetricAlarm().withAlarmActions(arns)
    def alarmsResponse = new DescribeAlarmsResult().withMetricAlarms(metricAlarm)

    when:
    op.operate([])

    then:
    1 * autoScaling.deletePolicy(new DeletePolicyRequest(
        policyName: "scalingPolicy1",
        autoScalingGroupName: "kato-main-v000"
    ))
    1 * autoScaling.describePolicies(new DescribePoliciesRequest()
        .withPolicyNames(description.policyName)
        .withAutoScalingGroupName(description.serverGroupName)) >> policyResponse
    1 * cloudWatch.describeAlarms(new DescribeAlarmsRequest().withAlarmNames("the-alarm")) >> alarmsResponse
    0 * _

    where:
    arns << [ ["policy:arn", "otherpolicy:arn"], ["otherpolicy:arn"]]
  }

}
