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

import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult
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
    final request = new PutScalingPolicyRequest(
      policyName: policyName,
      autoScalingGroupName: description.serverGroupName,
    )
    if (description.targetTrackingConfiguration) {
      request.withTargetTrackingConfiguration(description.targetTrackingConfiguration)
        .withEstimatedInstanceWarmup(description.estimatedInstanceWarmup)
        .withPolicyType(PolicyType.TargetTrackingScaling.toString())
    } else {
      request.withAdjustmentType(description.adjustmentType.toString())
        .withMinAdjustmentMagnitude(description.minAdjustmentMagnitude)

      if (description.step) {
        request.withPolicyType(PolicyType.StepScaling.toString()).
          withEstimatedInstanceWarmup(description.step.estimatedInstanceWarmup).
          withStepAdjustments(description.step.stepAdjustments).
          withMetricAggregationType(description.step.metricAggregationType.toString())
      } else {
        request.withPolicyType(PolicyType.SimpleScaling.toString()).
          withCooldown(description.simple.cooldown).
          withScalingAdjustment(description.simple.scalingAdjustment)
      }
    }

    final autoScaling = amazonClientProvider.getAutoScaling(description.credentials, description.region, true)
    PutScalingPolicyResult scalingPolicyResult = autoScaling.putScalingPolicy(request)

    if (description.alarm && !description.targetTrackingConfiguration) {
      addAlarm(scalingPolicyResult)
      new UpsertScalingPolicyResult(
          policyName: policyName.toString(),
          policyArn: scalingPolicyResult?.policyARN,
          alarmName: description.alarm.name
      )
    } else {
      new UpsertScalingPolicyResult(
          policyName: policyName.toString(),
          policyArn: scalingPolicyResult?.policyARN
      )
    }
  }

  private void addAlarm(PutScalingPolicyResult scalingPolicyResult) {
    def alarm = description.alarm
    alarm.name = alarm.name ?: "${description.serverGroupName}-alarm-${description.alarm.metricName}-${idGenerator.nextId()}"
    alarm.alarmActionArns = alarm.alarmActionArns ?: []
    if (!alarm.alarmActionArns.contains(scalingPolicyResult.policyARN)) {
      alarm.alarmActionArns.add(scalingPolicyResult.policyARN)
    }
    def request = description.alarm.buildRequest()
    def cloudWatch = amazonClientProvider.getCloudWatch(description.credentials, description.region, true)
    cloudWatch.putMetricAlarm(request)
  }

  enum PolicyType {
    SimpleScaling, StepScaling, TargetTrackingScaling
  }

}
