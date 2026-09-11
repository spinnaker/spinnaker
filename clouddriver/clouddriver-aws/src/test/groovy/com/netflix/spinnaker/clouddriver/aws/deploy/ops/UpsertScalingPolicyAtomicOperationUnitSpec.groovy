/*
 * Copyright 2016 Netflix, Inc.
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
import software.amazon.awssdk.services.autoscaling.model.PutScalingPolicyRequest
import software.amazon.awssdk.services.autoscaling.model.PutScalingPolicyResponse
import software.amazon.awssdk.services.autoscaling.model.StepAdjustment
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AdjustmentType
import com.netflix.spinnaker.clouddriver.aws.deploy.description.MetricAggregationType
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAlarmDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertScalingPolicyDescription
import spock.lang.Specification
import spock.lang.Subject

class UpsertScalingPolicyAtomicOperationUnitSpec extends Specification {
  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def description = new UpsertScalingPolicyDescription(
    serverGroupName: "kato-main-v000",
    region: "us-west-1",
    adjustmentType: AdjustmentType.PercentChangeInCapacity,
    minAdjustmentMagnitude: 3,
    simple: new UpsertScalingPolicyDescription.Simple(
      scalingAdjustment: 5,
      cooldown: 1
    )
  )

  def autoScaling = Mock(AutoScalingClient)
  def cloudWatch = Mock(CloudWatchClient)
  def amazonClientProvider = Stub(AmazonClientProvider) {
    getAutoScalingV2(_, _) >> autoScaling
    getAmazonCloudWatchV2(_, _) >> cloudWatch
  }

  @Subject def op = new UpsertScalingPolicyAtomicOperation(description)

  def setup() {
    op.amazonClientProvider = amazonClientProvider
    op.IdGenerator = new IdGenerator() {
      int nextId = 0
      String nextId() {
        ++nextId
      }
    }
  }

  void "creates unnamed scaling policy"() {

    when:
    final result = op.operate([])

    then:
    1 * autoScaling.putScalingPolicy(PutScalingPolicyRequest.builder()
      .policyName("kato-main-v000-policy-1")
      .autoScalingGroupName("kato-main-v000")
      .adjustmentType("PercentChangeInCapacity")
      .cooldown(1)
      .minAdjustmentMagnitude(3)
      .scalingAdjustment(5)
      .policyType("SimpleScaling")
      .build()) >> {
      PutScalingPolicyResponse.builder().policyARN("arn").build()
    }

    result == new UpsertScalingPolicyResult(policyArn: "arn", policyName: "kato-main-v000-policy-1")
  }

  void "creates unnamed step scaling policy"() {
    given:
    description.step = new UpsertScalingPolicyDescription.Step(
      estimatedInstanceWarmup: 2,
      metricAggregationType: MetricAggregationType.Average,
      stepAdjustments: [
            StepAdjustment.builder().metricIntervalLowerBound(100).metricIntervalUpperBound(200).scalingAdjustment(30).build()
      ]
    )

    when:
    final result = op.operate([])

    then:
    1 * autoScaling.putScalingPolicy(PutScalingPolicyRequest.builder()
      .policyName("kato-main-v000-policy-1")
      .autoScalingGroupName("kato-main-v000")
      .adjustmentType("PercentChangeInCapacity")
      .estimatedInstanceWarmup(2)
      .minAdjustmentMagnitude(3)
      .stepAdjustments([
        StepAdjustment.builder().metricIntervalLowerBound(100).metricIntervalUpperBound(200).scalingAdjustment(30).build()
      ])
      .metricAggregationType("Average")
      .policyType("StepScaling")
      .build()) >> {
      PutScalingPolicyResponse.builder().policyARN("arn").build()
    }

    result == new UpsertScalingPolicyResult(policyArn: "arn", policyName: "kato-main-v000-policy-1")
  }


  void "updates named scaling policy"() {
    given:
    description.name = "existingPolicy"

    when:
    final result = op.operate([])

    then:
    1 * autoScaling.putScalingPolicy(PutScalingPolicyRequest.builder()
      .policyName("existingPolicy")
      .autoScalingGroupName("kato-main-v000")
      .adjustmentType("PercentChangeInCapacity")
      .cooldown(1)
      .minAdjustmentMagnitude(3)
      .scalingAdjustment(5)
      .policyType("SimpleScaling")
      .build()) >> {
      PutScalingPolicyResponse.builder().policyARN("arn").build()
    }

    result == new UpsertScalingPolicyResult(policyArn: "arn", policyName: "existingPolicy")
  }

  void "updates alarm if included"() {
    given:
    def alarm = new UpsertAlarmDescription(name: "existing-alarm", namespace: "amazon/ec2", alarmActionArns: ["arn"])
    description.alarm = alarm
    description.name = "existingPolicy"

    when:
    final result = op.operate([])

    then:
    1 * autoScaling.putScalingPolicy(PutScalingPolicyRequest.builder()
      .policyName("existingPolicy")
      .autoScalingGroupName("kato-main-v000")
      .adjustmentType("PercentChangeInCapacity")
      .cooldown(1)
      .minAdjustmentMagnitude(3)
      .scalingAdjustment(5)
      .policyType("SimpleScaling")
      .build()) >> {
      PutScalingPolicyResponse.builder().policyARN("arn").build()
    }

    1 * cloudWatch.putMetricAlarm(alarm.buildRequest())

    result == new UpsertScalingPolicyResult(
        policyArn: "arn", policyName: "existingPolicy", alarmName: "existing-alarm")

  }

  void "adds policy to alarm actions if not already present"() {
    given:
    def alarm = new UpsertAlarmDescription(name: "existing-alarm", namespace: "amazon/ec2", alarmActionArns: ["barn"])
    description.alarm = alarm
    description.name = "existingPolicy"

    when:
    final result = op.operate([])

    then:
    1 * autoScaling.putScalingPolicy(PutScalingPolicyRequest.builder()
      .policyName("existingPolicy")
      .autoScalingGroupName("kato-main-v000")
      .adjustmentType("PercentChangeInCapacity")
      .cooldown(1)
      .minAdjustmentMagnitude(3)
      .scalingAdjustment(5)
      .policyType("SimpleScaling")
      .build()) >> {
      PutScalingPolicyResponse.builder().policyARN("arn").build()
    }

    1 * cloudWatch.putMetricAlarm(_)
    alarm.alarmActionArns.sort() == ["arn", "barn"]

    result == new UpsertScalingPolicyResult(
        policyArn: "arn", policyName: "existingPolicy", alarmName: "existing-alarm")

  }

}
