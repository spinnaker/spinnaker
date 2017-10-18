/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.titus.deploy.description

import com.amazonaws.services.autoscaling.model.StepAdjustment
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAlarmDescription
import com.netflix.titus.grpc.protogen.AlarmConfiguration
import com.netflix.titus.grpc.protogen.AlarmConfiguration.ComparisonOperator
import com.netflix.titus.grpc.protogen.AlarmConfiguration.Statistic
import com.netflix.titus.grpc.protogen.CustomizedMetricSpecification
import com.netflix.titus.grpc.protogen.MetricDimension
import com.netflix.titus.grpc.protogen.PredefinedMetricSpecification
import com.netflix.titus.grpc.protogen.ScalingPolicy
import com.netflix.titus.grpc.protogen.ScalingPolicy.Builder
import com.netflix.titus.grpc.protogen.ScalingPolicyResult
import com.netflix.titus.grpc.protogen.StepAdjustments
import com.netflix.titus.grpc.protogen.StepScalingPolicy
import com.netflix.titus.grpc.protogen.StepScalingPolicy.AdjustmentType
import com.netflix.titus.grpc.protogen.StepScalingPolicy.MetricAggregationType
import com.netflix.titus.grpc.protogen.StepScalingPolicyDescriptor
import com.netflix.titus.grpc.protogen.TargetTrackingPolicyDescriptor

class UpsertTitusScalingPolicyDescription extends AbstractTitusCredentialsDescription {
  // required
  String region
  String jobId
  AdjustmentType adjustmentType = AdjustmentType.ChangeInCapacity

  // optional
  String scalingPolicyID
  Integer minAdjustmentMagnitude

  Step step
  TargetTrackingConfiguration targetTrackingConfiguration

  UpsertAlarmDescription alarm

  static class Step {
    Collection<StepAdjustment> stepAdjustments
    Integer cooldown
    MetricAggregationType metricAggregationType
  }

  static class TargetTrackingConfiguration {
    Double targetValue
    com.amazonaws.services.applicationautoscaling.model.PredefinedMetricSpecification predefinedMetricSpecification
    com.amazonaws.services.applicationautoscaling.model.CustomizedMetricSpecification customizedMetricSpecification
    Integer scaleOutCooldown
    Integer scaleInCooldown
    Boolean disableScaleIn
  }

  Builder toScalingPolicyBuilder() {
    Builder policyBuilder = ScalingPolicy.newBuilder()
    if (targetTrackingConfiguration) {
      TargetTrackingPolicyDescriptor.Builder ttpBuilder = TargetTrackingPolicyDescriptor.newBuilder()
      ttpBuilder
        .setDisableScaleIn(targetTrackingConfiguration.disableScaleIn)
        .setTargetValue(targetTrackingConfiguration.targetValue)
        .setScaleOutCooldownSec(targetTrackingConfiguration.scaleOutCooldown)
        .setScaleInCooldownSec(targetTrackingConfiguration.scaleInCooldown)

      if (targetTrackingConfiguration.predefinedMetricSpecification) {
        ttpBuilder.setPredefinedMetricSpecification(
          PredefinedMetricSpecification.newBuilder()
            .setPredefinedMetricType(targetTrackingConfiguration.predefinedMetricSpecification.predefinedMetricType)
            .setResourceLabel(targetTrackingConfiguration.predefinedMetricSpecification.resourceLabel)
        )
      }

      if (targetTrackingConfiguration.customizedMetricSpecification) {
        def metricSpecification = targetTrackingConfiguration.customizedMetricSpecification
        CustomizedMetricSpecification.Builder metricBuilder = CustomizedMetricSpecification.newBuilder()
        metricBuilder
          .setMetricName(metricSpecification.metricName)
          .setNamespace(metricSpecification.namespace)
          .setStatistic(Statistic.valueOf(metricSpecification.statistic))

        if (metricSpecification.unit) {
          metricBuilder.setUnit(metricSpecification.unit)
        } else {
          metricBuilder.setUnit("None")
        }

        metricSpecification.dimensions.each {
          metricBuilder.addDimensions(
            MetricDimension.newBuilder()
              .setName(it.name).
              setValue(it.value)
          )
        }
        ttpBuilder.setCustomizedMetricSpecification(metricBuilder)
      }

      policyBuilder.setTargetPolicyDescriptor(ttpBuilder)
    } else {
      StepScalingPolicy.Builder stepBuilder = StepScalingPolicy.newBuilder()
      stepBuilder.setAdjustmentType(AdjustmentType.valueOf(adjustmentType.name()))
        .setMetricAggregationType(MetricAggregationType.valueOf(step.metricAggregationType.name()))
        .setCooldownSec(step.cooldown)

      if (minAdjustmentMagnitude != null) {
        stepBuilder.setMinAdjustmentMagnitude(minAdjustmentMagnitude)
      }
      step.stepAdjustments.each { adjustment ->
        StepAdjustments.Builder adjustmentBuilder = StepAdjustments.newBuilder()
        if (adjustment.metricIntervalLowerBound != null) {
          adjustmentBuilder.setMetricIntervalLowerBound(adjustment.metricIntervalLowerBound)
        }
        if (adjustment.metricIntervalUpperBound != null) {
          adjustmentBuilder.setMetricIntervalUpperBound(adjustment.metricIntervalUpperBound)
        }
        adjustmentBuilder.setScalingAdjustment(adjustment.scalingAdjustment)
        stepBuilder.addStepAdjustments(adjustmentBuilder)
      }

      AlarmConfiguration.Builder alarmBuilder = AlarmConfiguration.newBuilder()
        .setActionsEnabled(true)
        .setComparisonOperator(ComparisonOperator.valueOf(alarm.comparisonOperator.name()))
        .setEvaluationPeriods(alarm.evaluationPeriods)
        .setMetricName(alarm.metricName)
        .setMetricNamespace(alarm.namespace)
        .setPeriodSec(alarm.period)
        .setStatistic(Statistic.valueOf(alarm.statistic.name()))
        .setThreshold(alarm.threshold)

      StepScalingPolicyDescriptor.Builder sspBuilder = StepScalingPolicyDescriptor.newBuilder()
        .setScalingPolicy(stepBuilder)
        .setAlarmConfig(alarmBuilder)

      policyBuilder.setStepPolicyDescriptor(sspBuilder)
    }
    return policyBuilder
  }

  static UpsertTitusScalingPolicyDescription fromScalingPolicyResult(String region, ScalingPolicyResult result) {
    UpsertTitusScalingPolicyDescription description = new UpsertTitusScalingPolicyDescription()
    description.region = region
    description.jobId = result.jobId

    StepScalingPolicyDescriptor stepDescriptor = result.scalingPolicy.stepPolicyDescriptor
    if (stepDescriptor) {
      StepScalingPolicy stepPolicy = stepDescriptor.scalingPolicy
      Step step = new Step()
      description.step = step
      step.cooldown = stepPolicy.cooldownSec.value
      step.metricAggregationType = stepPolicy.metricAggregationType
      step.stepAdjustments = []
      stepPolicy.stepAdjustmentsList?.each {
        com.amazonaws.services.applicationautoscaling.model.StepAdjustment adjustment = new com.amazonaws.services.applicationautoscaling.model.StepAdjustment()
        adjustment.scalingAdjustment = it.scalingAdjustment.value
        adjustment.metricIntervalLowerBound = it.metricIntervalLowerBound?.value
        if (it.metricIntervalUpperBound.value > 0) {
          adjustment.metricIntervalUpperBound = it.metricIntervalUpperBound?.value
        }
        step.stepAdjustments << adjustment
      }
      AlarmConfiguration alarmConfig = stepDescriptor.alarmConfig
      UpsertAlarmDescription alarm = new UpsertAlarmDescription()
      description.alarm = alarm
      alarm.region = region
      alarm.threshold = alarmConfig.threshold?.value
      alarm.period = alarmConfig.periodSec?.value
      alarm.actionsEnabled = true
      alarm.evaluationPeriods = alarmConfig.evaluationPeriods?.value
      alarm.comparisonOperator = com.amazonaws.services.cloudwatch.model.ComparisonOperator.valueOf(alarmConfig.comparisonOperator.name())
      alarm.namespace = alarmConfig.metricNamespace
      alarm.metricName = alarmConfig.metricName
      alarm.statistic = com.amazonaws.services.cloudwatch.model.Statistic.valueOf(alarmConfig.statistic.name())
    }

    description
  }
}
