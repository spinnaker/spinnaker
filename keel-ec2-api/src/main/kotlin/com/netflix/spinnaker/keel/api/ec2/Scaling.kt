/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import java.time.Duration

val DEFAULT_AUTOSCALE_INSTANCE_WARMUP: Duration = Duration.ofMinutes(5)

data class Scaling(
  val suspendedProcesses: Set<ScalingProcess> = emptySet(),
  val targetTrackingPolicies: Set<TargetTrackingPolicy> = emptySet(),
  val stepScalingPolicies: Set<StepScalingPolicy> = emptySet()
) {
  fun hasScalingPolicies(): Boolean =
    targetTrackingPolicies.isNotEmpty() || stepScalingPolicies.isNotEmpty()
}

sealed class ScalingPolicy

data class TargetTrackingPolicy(
  @get:ExcludedFromDiff
  val name: String? = null,
  val warmup: Duration = DEFAULT_AUTOSCALE_INSTANCE_WARMUP,
  val targetValue: Double,
  val disableScaleIn: Boolean? = null,
  val predefinedMetricSpec: PredefinedMetricSpecification? = null,
  val customMetricSpec: CustomizedMetricSpecification? = null
) : ScalingPolicy() {
  init {
    require(customMetricSpec != null || predefinedMetricSpec != null) {
      "a custom or predefined metric must be defined"
    }

    require(customMetricSpec == null || predefinedMetricSpec == null) {
      "only one of customMetricSpec or predefinedMetricSpec can be defined"
    }
  }

  // Excluding name, so we can remove policies from current asg when modifying
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TargetTrackingPolicy

    if (warmup != other.warmup) return false
    if (targetValue != other.targetValue) return false
    if (disableScaleIn != other.disableScaleIn) return false
    if (predefinedMetricSpec != other.predefinedMetricSpec) return false
    if (customMetricSpec != other.customMetricSpec) return false

    return true
  }

  override fun hashCode(): Int {
    var result = warmup.hashCode()
    result = 31 * result + targetValue.hashCode()
    result = 31 * result + disableScaleIn.hashCode()
    result = 31 * result + predefinedMetricSpec.hashCode()
    result = 31 * result + customMetricSpec.hashCode()
    return result
  }
}

data class StepScalingPolicy(
  @get:ExcludedFromDiff
  val name: String? = null,
  val adjustmentType: String,
  val actionsEnabled: Boolean,
  val comparisonOperator: String,
  val dimensions: Set<MetricDimension>? = emptySet(),
  val evaluationPeriods: Int,
  val period: Duration,
  val threshold: Int,
  val metricName: String,
  val namespace: String,
  val statistic: String,
  val warmup: Duration = DEFAULT_AUTOSCALE_INSTANCE_WARMUP,
  val metricAggregationType: String = "Average",
  val stepAdjustments: Set<StepAdjustment>
) : ScalingPolicy() {
  init {
    require(stepAdjustments.isNotEmpty()) { "at least one stepAdjustment is required" }
    require(dimensions.isNullOrEmpty() || dimensions.none { it.name == "AutoScalingGroupName" }) {
      "autoscale dimensions should not be scoped to a specific ASG name"
    }
    require(period.multipliedBy(evaluationPeriods.toLong()) <= Duration.ofDays(1)) {
      "period * evaluationPeriods must be less than or equal to 1 day"
    }
  }

  // Excluding name, so we can remove policies from current asg when modifying
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as StepScalingPolicy

    if (adjustmentType != other.adjustmentType) return false
    if (actionsEnabled != other.actionsEnabled) return false
    if (comparisonOperator != other.comparisonOperator) return false
    if (dimensions != other.dimensions) return false
    if (evaluationPeriods != other.evaluationPeriods) return false
    if (period != other.period) return false
    if (threshold != other.threshold) return false
    if (metricName != other.metricName) return false
    if (namespace != other.namespace) return false
    if (statistic != other.statistic) return false
    if (warmup != other.warmup) return false
    if (metricAggregationType != other.metricAggregationType) return false
    if (stepAdjustments != other.stepAdjustments) return false

    return true
  }

  override fun hashCode(): Int {
    var result = adjustmentType.hashCode()
    result = 31 * result + actionsEnabled.hashCode()
    result = 31 * result + comparisonOperator.hashCode()
    result = 31 * result + dimensions.hashCode()
    result = 31 * result + evaluationPeriods.hashCode()
    result = 31 * result + period.hashCode()
    result = 31 * result + threshold.hashCode()
    result = 31 * result + metricName.hashCode()
    result = 31 * result + namespace.hashCode()
    result = 31 * result + statistic.hashCode()
    result = 31 * result + warmup.hashCode()
    result = 31 * result + metricAggregationType.hashCode()
    result = 31 * result + stepAdjustments.hashCode()
    return result
  }
}

data class MetricDimension(
  val name: String,
  val value: String
)

// https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_CustomizedMetricSpecification.html
data class CustomizedMetricSpecification(
  val name: String,
  val namespace: String,
  val statistic: String,
  val unit: String? = null,
  val dimensions: Set<MetricDimension>? = emptySet()
)

// https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_PredefinedMetricSpecification.html
data class PredefinedMetricSpecification(
  val type: String,
  val label: String? = null
)

// https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_StepAdjustment.html
data class StepAdjustment(
  val lowerBound: Double? = null,
  val upperBound: Double? = null,
  val scalingAdjustment: Int
)
