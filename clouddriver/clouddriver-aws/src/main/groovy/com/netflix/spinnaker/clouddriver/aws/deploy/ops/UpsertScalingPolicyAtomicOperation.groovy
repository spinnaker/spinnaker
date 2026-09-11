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

import software.amazon.awssdk.services.autoscaling.model.PutScalingPolicyRequest
import software.amazon.awssdk.services.autoscaling.model.PutScalingPolicyResponse
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertScalingPolicyDescription
import org.springframework.beans.factory.annotation.Autowired

class UpsertScalingPolicyAtomicOperation implements AtomicOperation<UpsertScalingPolicyResult> {

  UpsertScalingPolicyDescription description

  UpsertScalingPolicyAtomicOperation(UpsertScalingPolicyDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  IdGenerator IdGenerator = new IdGenerator()

  @Override
  UpsertScalingPolicyResult operate(List priorOutputs) {
    final policyName = description.name ?: "${description.serverGroupName}-policy-${idGenerator.nextId()}"
    final requestBuilder = PutScalingPolicyRequest.builder()
      .policyName(policyName)
      .autoScalingGroupName(description.serverGroupName)

    if (description.targetTrackingConfiguration) {
      requestBuilder.targetTrackingConfiguration(description.targetTrackingConfiguration)
        .estimatedInstanceWarmup(description.estimatedInstanceWarmup)
        .policyType(PolicyType.TargetTrackingScaling.toString())
    } else {
      requestBuilder.adjustmentType(description.adjustmentType.toString())
        .minAdjustmentMagnitude(description.minAdjustmentMagnitude)

      if (description.step) {
        requestBuilder.policyType(PolicyType.StepScaling.toString())
          .estimatedInstanceWarmup(description.step.estimatedInstanceWarmup)
          .stepAdjustments(description.step.stepAdjustments)
          .metricAggregationType(description.step.metricAggregationType.toString())
      } else {
        requestBuilder.policyType(PolicyType.SimpleScaling.toString())
          .cooldown(description.simple.cooldown)
          .scalingAdjustment(description.simple.scalingAdjustment)
      }
    }

    final autoScaling = amazonClientProvider.getAutoScalingV2(description.credentials, description.region)
    PutScalingPolicyResponse scalingPolicyResult = autoScaling.putScalingPolicy(requestBuilder.build())

    if (description.alarm && !description.targetTrackingConfiguration) {
      addAlarm(scalingPolicyResult)
      new UpsertScalingPolicyResult(
          policyName: policyName.toString(),
          policyArn: scalingPolicyResult?.policyARN(),
          alarmName: description.alarm.name
      )
    } else {
      new UpsertScalingPolicyResult(
          policyName: policyName.toString(),
          policyArn: scalingPolicyResult?.policyARN()
      )
    }
  }

  private void addAlarm(PutScalingPolicyResponse scalingPolicyResult) {
    def alarm = description.alarm
    alarm.name = alarm.name ?: "${description.serverGroupName}-alarm-${description.alarm.metricName}-${idGenerator.nextId()}"
    alarm.alarmActionArns = alarm.alarmActionArns ?: []
    if (!alarm.alarmActionArns.contains(scalingPolicyResult.policyARN())) {
      alarm.alarmActionArns.add(scalingPolicyResult.policyARN())
    }
    def request = description.alarm.buildRequest()
    def cloudWatch = amazonClientProvider.getAmazonCloudWatchV2(description.credentials, description.region)
    cloudWatch.putMetricAlarm(request)
  }

  enum PolicyType {
    SimpleScaling, StepScaling, TargetTrackingScaling
  }

}
