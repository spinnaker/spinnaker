package com.netflix.spinnaker.keel.clouddriver.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.netflix.spinnaker.keel.api.Capacity
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache

// todo eb: this should be more general so that it works for all server groups, not just ec2
data class ActiveServerGroup(
  val name: String,
  val region: String,
  val zones: Set<String>,
  val image: ActiveServerGroupImage,
  val launchConfig: LaunchConfig,
  val asg: AutoScalingGroup,
  val scalingPolicies: List<ScalingPolicy>,
  val vpcId: String,
  val targetGroups: Set<String>,
  val loadBalancers: Set<String>,
  val capacity: Capacity,
  val cloudProvider: String,
  val securityGroups: Set<String>,
  val accountName: String,
  val moniker: Moniker,
  val buildInfo: BuildInfo? = null
)

fun ActiveServerGroup.subnet(cloudDriverCache: CloudDriverCache): String =
  asg.vpczoneIdentifier.substringBefore(",").let { subnetId ->
    cloudDriverCache
      .subnetBy(subnetId)
      .purpose ?: error("Subnet $subnetId has no purpose!")
  }

data class ActiveServerGroupImage(
  val imageId: String,
  val appVersion: String?,
  val baseImageVersion: String?
) {
  @JsonCreator
  constructor(
    imageId: String,
    tags: List<Map<String, Any?>>
  ) : this(
    imageId,
    appVersion = tags.getTag("appversion")?.substringBefore("/"),
    baseImageVersion = tags.getTag("base_ami_version")
  )
}

private fun List<Map<String, Any?>>.getTag(key: String) =
  firstOrNull { it["key"] == key }
    ?.get("value")
    ?.toString()

class RequiredTagMissing(tagName: String, imageId: String) :
  RuntimeException("Required tag \"$tagName\" was not found on AMI $imageId")

data class LaunchConfig(
  val ramdiskId: String?,
  val ebsOptimized: Boolean,
  val imageId: String,
  val instanceType: String,
  val keyName: String,
  val iamInstanceProfile: String,
  val instanceMonitoring: InstanceMonitoring
)

data class AutoScalingGroup(
  val autoScalingGroupName: String,
  val defaultCooldown: Long,
  val healthCheckType: String,
  val healthCheckGracePeriod: Long,
  val suspendedProcesses: Set<SuspendedProcess>,
  val enabledMetrics: Set<String>,
  val tags: Set<Tag>,
  val terminationPolicies: Set<String>,
  val vpczoneIdentifier: String
)

// https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_ScalingPolicy.html
data class ScalingPolicy(
  val autoScalingGroupName: String,
  val policyName: String,
  val policyType: String,
  val estimatedInstanceWarmup: Int,
  val adjustmentType: String? = null, // step scaling only
  val minAdjustmentStep: Int? = null, // step scaling only
  val minAdjustmentMagnitude: Int? = null, // step only
  val stepAdjustments: List<StepAdjustmentModel>? = null,
  val metricAggregationType: String? = null,
  val targetTrackingConfiguration: TargetTrackingConfiguration? = null,
  val alarms: List<ScalingPolicyAlarm>
)

// https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_StepAdjustment.html
data class StepAdjustmentModel(
  val metricIntervalLowerBound: Double? = null,
  val metricIntervalUpperBound: Double? = null,
  val scalingAdjustment: Int
)

// these are managed by EC2 for target tracking policies, but client provided for step policies
data class ScalingPolicyAlarm(
  val actionsEnabled: Boolean = true,
  val comparisonOperator: String,
  val dimensions: List<MetricDimensionModel>? = emptyList(),
  val evaluationPeriods: Int,
  val period: Int,
  val threshold: Int,
  val metricName: String,
  val namespace: String,
  val statistic: String
)

// https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_TargetTrackingConfiguration.html
data class TargetTrackingConfiguration(
  val targetValue: Double,
  val disableScaleIn: Boolean? = null,
  val customizedMetricSpecification: CustomizedMetricSpecificationModel? = null,
  val predefinedMetricSpecification: PredefinedMetricSpecificationModel? = null
)

// https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_CustomizedMetricSpecification.html
data class CustomizedMetricSpecificationModel(
  val metricName: String,
  val namespace: String,
  val statistic: String,
  val unit: String? = null,
  val dimensions: List<MetricDimensionModel>? = emptyList()
)

// https://docs.aws.amazon.com/autoscaling/ec2/APIReference/API_PredefinedMetricSpecification.html
data class PredefinedMetricSpecificationModel(
  val predefinedMetricType: String,
  val resourceLabel: String? = null
)

data class MetricDimensionModel(
  val name: String,
  val value: String
)

data class SuspendedProcess(
  val processName: String,
  val suspensionReason: String? = null
)

data class Tag(
  val key: String,
  val value: String
)

data class InstanceMonitoring(
  val enabled: Boolean
)

data class BuildInfo(
  @JsonAlias("package_name")
  val packageName: String?
)
