package com.netflix.spinnaker.keel.clouddriver.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.kork.exceptions.SystemException

data class ServerGroupCollection<T : BaseServerGroup>(
  val accountName: String,
  val serverGroups: Set<T>
)

/**
 * Fields common to all of the different kinds of server groups (EC2, Titus)
 */
interface BaseServerGroup {
  val name: String
  val region: String
  val targetGroups: Set<String>
  val loadBalancers: Set<String>
  val capacity: Capacity
  val cloudProvider: String
  val securityGroups: Set<String>
  val moniker: Moniker
  val disabled: Boolean
    get() = false
  val instanceCounts: InstanceCounts
}

data class InstanceCounts(
  val total: Int,
  val up: Int,
  val down: Int,
  val unknown: Int,
  val outOfService: Int,
  val starting: Int
) {
  // active asg is healthy if all instances are up
  fun isHealthy(): Boolean =
    up == total
}

/**
 * Fields common to classes that model EC2 server groups
 */
interface BaseEc2ServerGroup : BaseServerGroup {
  val zones: Set<String>
  val image: ActiveServerGroupImage
  val launchConfig: LaunchConfig
  val asg: AutoScalingGroup
  val scalingPolicies: List<ScalingPolicy>
  val vpcId: String
  val buildInfo: BuildInfo?
}

/**
 * Objects that are returned when querying for all of the ec2 server groups associated with a cluster.
 *
 * Two differences from [ActiveServerGroup]:
 *   - disabled flag
 *   - no accountName field (since this is defined on the parent [ServerGroupCollection] object
 */
data class ServerGroup(
  override val name: String,
  override val region: String,
  override val zones: Set<String>,
  override val image: ActiveServerGroupImage,
  override val launchConfig: LaunchConfig,
  override val asg: AutoScalingGroup,
  override val scalingPolicies: List<ScalingPolicy>,
  override val vpcId: String,
  override val targetGroups: Set<String>,
  override val loadBalancers: Set<String>,
  override val capacity: Capacity,
  override val cloudProvider: String,
  override val securityGroups: Set<String>,
  override val moniker: Moniker,
  override val buildInfo: BuildInfo? = null,
  override val disabled: Boolean,
  override val instanceCounts: InstanceCounts
) : BaseEc2ServerGroup

fun ServerGroup.toActive(accountName: String) =
  ActiveServerGroup(
    name = name,
    region = region,
    zones = zones,
    image = image,
    launchConfig = launchConfig,
    asg = asg,
    scalingPolicies = scalingPolicies,
    vpcId = vpcId,
    targetGroups = targetGroups,
    loadBalancers = loadBalancers,
    capacity = capacity,
    cloudProvider = cloudProvider,
    securityGroups = securityGroups,
    accountName = accountName,
    moniker = moniker,
    buildInfo = buildInfo,
    instanceCounts = instanceCounts
  )

// todo eb: this should be more general so that it works for all server groups, not just ec2
data class ActiveServerGroup(
  override val name: String,
  override val region: String,
  override val zones: Set<String>,
  override val image: ActiveServerGroupImage,
  override val launchConfig: LaunchConfig,
  override val asg: AutoScalingGroup,
  override val scalingPolicies: List<ScalingPolicy>,
  override val vpcId: String,
  override val targetGroups: Set<String>,
  override val loadBalancers: Set<String>,
  override val capacity: Capacity,
  override val cloudProvider: String,
  override val securityGroups: Set<String>,
  val accountName: String,
  override val moniker: Moniker,
  override val buildInfo: BuildInfo? = null,
  override val instanceCounts: InstanceCounts
) : BaseEc2ServerGroup

fun ActiveServerGroup.subnet(cloudDriverCache: CloudDriverCache): String =
  asg.vpczoneIdentifier.substringBefore(",").let { subnetId ->
    cloudDriverCache
      .subnetBy(subnetId)
      .purpose ?: error("Subnet $subnetId has no purpose!")
  }

data class ActiveServerGroupImage(
  val imageId: String,
  val appVersion: String?,
  val baseImageVersion: String?,
  val name: String,
  val imageLocation: String,
  val description: String?
) {
  @JsonCreator
  constructor(
    imageId: String,
    name: String,
    imageLocation: String,
    description: String?,
    tags: List<Map<String, Any?>>
  ) : this(
    imageId = imageId,
    appVersion = tags.getTag("appversion")?.substringBefore("/"),
    baseImageVersion = tags.getTag("base_ami_version"),
    name = name,
    imageLocation = imageLocation,
    description = description
  )
}

private fun List<Map<String, Any?>>.getTag(key: String) =
  firstOrNull { it["key"] == key }
    ?.get("value")
    ?.toString()

class RequiredTagMissing(tagName: String, imageId: String) :
  SystemException("Required tag \"$tagName\" was not found on AMI $imageId")

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
