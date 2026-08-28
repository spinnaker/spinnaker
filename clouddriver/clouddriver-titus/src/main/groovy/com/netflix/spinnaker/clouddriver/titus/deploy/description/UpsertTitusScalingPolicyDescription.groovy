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

import software.amazon.awssdk.services.applicationautoscaling.model.CustomizedMetricSpecification as AwsCustomizedMetricSpecification
import software.amazon.awssdk.services.applicationautoscaling.model.MetricDimension as AwsMetricDimension
import software.amazon.awssdk.services.applicationautoscaling.model.PredefinedMetricSpecification as AwsPredefinedMetricSpecification
import software.amazon.awssdk.services.applicationautoscaling.model.StepAdjustment as AwsStepAdjustment
import software.amazon.awssdk.services.autoscaling.model.StepAdjustment
import com.google.protobuf.BoolValue
import com.google.protobuf.DoubleValue
import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAlarmDescription
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable
import com.netflix.titus.grpc.protogen.*
import com.netflix.titus.grpc.protogen.AlarmConfiguration.ComparisonOperator
import com.netflix.titus.grpc.protogen.AlarmConfiguration.Statistic
import com.netflix.titus.grpc.protogen.ScalingPolicy.Builder
import com.netflix.titus.grpc.protogen.StepScalingPolicy.AdjustmentType
import com.netflix.titus.grpc.protogen.StepScalingPolicy.MetricAggregationType
import groovy.util.logging.Slf4j
import groovy.json.JsonOutput

@Slf4j
class UpsertTitusScalingPolicyDescription extends AbstractTitusCredentialsDescription implements ApplicationNameable {
  String application

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

  @Override
  Collection<String> getApplications() {
    return [application]
  }

  static class Step {
    Collection<StepAdjustment> stepAdjustments
    Integer cooldown
    MetricAggregationType metricAggregationType
  }

  static class TargetTrackingConfiguration {
    Double targetValue
    AwsPredefinedMetricSpecification predefinedMetricSpecification
    AwsCustomizedMetricSpecification customizedMetricSpecification
    Integer scaleOutCooldown
    Integer scaleInCooldown
    Boolean disableScaleIn
  }

  Builder toScalingPolicyBuilder() {
    Builder policyBuilder = ScalingPolicy.newBuilder()
    if (targetTrackingConfiguration) {
      TargetTrackingPolicyDescriptor.Builder ttpBuilder = TargetTrackingPolicyDescriptor.newBuilder()
      ttpBuilder
        .setDisableScaleIn(BoolValue.of(targetTrackingConfiguration.disableScaleIn))
        .setTargetValue(DoubleValue.of(targetTrackingConfiguration.targetValue))
        .setScaleOutCooldownSec(Int32Value.of(targetTrackingConfiguration.scaleOutCooldown))
        .setScaleInCooldownSec(Int32Value.of(targetTrackingConfiguration.scaleInCooldown))

      if (targetTrackingConfiguration.predefinedMetricSpecification) {
        ttpBuilder.setPredefinedMetricSpecification(
          PredefinedMetricSpecification.newBuilder()
            .setPredefinedMetricType(targetTrackingConfiguration.predefinedMetricSpecification.predefinedMetricTypeAsString())
            .setResourceLabel(targetTrackingConfiguration.predefinedMetricSpecification.resourceLabel())
        )
      }

      if (targetTrackingConfiguration.customizedMetricSpecification) {
        def metricSpecification = targetTrackingConfiguration.customizedMetricSpecification
        CustomizedMetricSpecification.Builder metricBuilder = CustomizedMetricSpecification.newBuilder()
        metricBuilder
          .setMetricName(metricSpecification.metricName())
          .setNamespace(metricSpecification.namespace())
          .setStatistic(Statistic.valueOf(metricSpecification.statisticAsString()))

        if (metricSpecification.unit()) {
          metricBuilder.setUnit(metricSpecification.unit())
        }

        metricSpecification.dimensions().each {
          metricBuilder.addDimensions(
            MetricDimension.newBuilder()
              .setName(it.name())
              .setValue(it.value())
          )
        }
        ttpBuilder.setCustomizedMetricSpecification(metricBuilder)
      }

      policyBuilder.setTargetPolicyDescriptor(ttpBuilder)
    } else {
      StepScalingPolicy.Builder stepBuilder = StepScalingPolicy.newBuilder()
      stepBuilder.setAdjustmentType(AdjustmentType.valueOf(adjustmentType.name()))
        .setMetricAggregationType(MetricAggregationType.valueOf(step.metricAggregationType.name()))
        .setCooldownSec(Int32Value.of(step.cooldown))

      if (minAdjustmentMagnitude != null) {
        stepBuilder.setMinAdjustmentMagnitude(Int64Value.of(minAdjustmentMagnitude))
      }
      step.stepAdjustments.each { adjustment ->
        StepAdjustments.Builder adjustmentBuilder = StepAdjustments.newBuilder()
        if (adjustment.metricIntervalLowerBound() != null) {
          adjustmentBuilder.setMetricIntervalLowerBound(DoubleValue.of(adjustment.metricIntervalLowerBound()))
        }
        if (adjustment.metricIntervalUpperBound() != null) {
          adjustmentBuilder.setMetricIntervalUpperBound(DoubleValue.of(adjustment.metricIntervalUpperBound()))
        }
        adjustmentBuilder.setScalingAdjustment(Int32Value.of(adjustment.scalingAdjustment()))
        stepBuilder.addStepAdjustments(adjustmentBuilder)
      }

      AlarmConfiguration.Builder alarmBuilder = AlarmConfiguration.newBuilder()
        .setActionsEnabled(BoolValue.of(true))
        .setComparisonOperator(ComparisonOperator.valueOf(alarm.comparisonOperator.name()))
        .setEvaluationPeriods(Int32Value.of(alarm.evaluationPeriods))
        .setMetricName(alarm.metricName)
        .setMetricNamespace(alarm.namespace)
        .setPeriodSec(Int32Value.of(alarm.period))
        .setStatistic(Statistic.valueOf(alarm.statistic.name()))
        .setThreshold(DoubleValue.of(alarm.threshold))

      StepScalingPolicyDescriptor.Builder sspBuilder = StepScalingPolicyDescriptor.newBuilder()
        .setScalingPolicy(stepBuilder)
        .setAlarmConfig(alarmBuilder)

      policyBuilder.setStepPolicyDescriptor(sspBuilder)
    }
    return policyBuilder
  }

  static UpsertTitusScalingPolicyDescription fromScalingPolicyResult(String region, ScalingPolicyResult result, String serverGroupName) {
    UpsertTitusScalingPolicyDescription description = new UpsertTitusScalingPolicyDescription()
    description.region = region
    description.jobId = result.jobId

    // if there's no scaling policy, it's a target tracking policy, not a step policy
    StepScalingPolicyDescriptor stepDescriptor = result.scalingPolicy.stepPolicyDescriptor
    if (stepDescriptor.hasScalingPolicy()) {
      StepScalingPolicy stepPolicy = stepDescriptor.scalingPolicy
      Step step = new Step()
      description.step = step
      description.adjustmentType = stepPolicy.adjustmentType
      step.cooldown = stepPolicy.cooldownSec.value
      step.metricAggregationType = stepPolicy.metricAggregationType
      step.stepAdjustments = []
      stepPolicy.stepAdjustmentsList?.each {
        def adjustmentBuilder = AwsStepAdjustment.builder().scalingAdjustment(it.scalingAdjustment.value)
        if (it.hasMetricIntervalLowerBound()) {
          adjustmentBuilder.metricIntervalLowerBound(it.metricIntervalLowerBound?.value)
        }
        if (it.hasMetricIntervalUpperBound()) {
          adjustmentBuilder.metricIntervalUpperBound(it.metricIntervalUpperBound?.value)
        }
        step.stepAdjustments << adjustmentBuilder.build()
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

    // Titus Target Tracking always uses customized metric specifications, so use that to determine if it's a target tracking policy
    TargetTrackingPolicyDescriptor targetDescriptor = result.scalingPolicy.targetPolicyDescriptor
    if (targetDescriptor.hasCustomizedMetricSpecification()) {
      TargetTrackingConfiguration targetTrackingConfiguration = new TargetTrackingConfiguration()
      description.targetTrackingConfiguration = targetTrackingConfiguration

      targetTrackingConfiguration.disableScaleIn = targetDescriptor.disableScaleIn.value
      targetTrackingConfiguration.scaleInCooldown = targetDescriptor.scaleInCooldownSec.value
      targetTrackingConfiguration.scaleOutCooldown = targetDescriptor.scaleInCooldownSec.value
      targetTrackingConfiguration.targetValue = targetDescriptor.targetValue.value

      CustomizedMetricSpecification sourceMetricSpecification = targetDescriptor.customizedMetricSpecification
      targetTrackingConfiguration.customizedMetricSpecification = AwsCustomizedMetricSpecification.builder()
        .metricName(sourceMetricSpecification.metricName)
        .namespace(sourceMetricSpecification.namespace)
        .statistic(sourceMetricSpecification.statistic.name())
        .unit(sourceMetricSpecification.unit)
        .dimensions(sourceMetricSpecification.dimensionsList.collect { dimension ->
        String value = dimension.name == "AutoScalingGroupName" ? serverGroupName : dimension.value
        AwsMetricDimension.builder().name(dimension.name).value(value).build()
      }).build()
    }
    log.info("UpsertTitusScalingPolicyDescription for ${serverGroupName} description: ${ JsonOutput.toJson(description) }")
    description
  }


}
