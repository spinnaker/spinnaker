package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroupImage
import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
import com.netflix.spinnaker.keel.clouddriver.model.Capacity
import com.netflix.spinnaker.keel.clouddriver.model.CustomizedMetricSpecificationModel
import com.netflix.spinnaker.keel.clouddriver.model.IamInstanceProfile
import com.netflix.spinnaker.keel.clouddriver.model.InstanceCounts
import com.netflix.spinnaker.keel.clouddriver.model.InstanceMonitoring
import com.netflix.spinnaker.keel.clouddriver.model.LaunchConfig
import com.netflix.spinnaker.keel.clouddriver.model.LaunchTemplate
import com.netflix.spinnaker.keel.clouddriver.model.LaunchTemplateData
import com.netflix.spinnaker.keel.clouddriver.model.MetricDimensionModel
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.ScalingPolicy
import com.netflix.spinnaker.keel.clouddriver.model.ScalingPolicyAlarm
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.clouddriver.model.SuspendedProcess
import com.netflix.spinnaker.keel.api.support.Tag
import com.netflix.spinnaker.keel.clouddriver.model.TargetTrackingConfiguration
import com.netflix.spinnaker.keel.core.parseMoniker
import org.apache.commons.lang3.RandomStringUtils
import com.netflix.spinnaker.keel.clouddriver.model.ServerGroup as ClouddriverServerGroup


/**
 * There are two possible types of payloads that can be returned from clouddriver
 *
 * The older launch configuration
 * The newer launch templates
 */
enum class LaunchInfo {
  LAUNCH_CONFIG,
  LAUNCH_TEMPLATE
}

fun ServerGroup.toCloudDriverResponse(
  vpc: Network,
  subnets: List<Subnet>,
  securityGroups: List<SecurityGroupSummary>,
  image: ServerGroup.ActiveServerGroupImage? = null,
  instanceCounts: ServerGroup.InstanceCounts = ServerGroup.InstanceCounts(1, 1, 0, 0, 0, 0),
  launchInfo: LaunchInfo = LaunchInfo.LAUNCH_CONFIG

): ActiveServerGroup =
  RandomStringUtils.randomNumeric(3).padStart(3, '0').let { sequence ->
    var launchConfig : LaunchConfig? = null
    var launchTemplate : LaunchTemplate? = null

    when(launchInfo) {
      LaunchInfo.LAUNCH_CONFIG -> {
        launchConfig = LaunchConfig(
          ebsOptimized=launchConfiguration.ebsOptimized,
          iamInstanceProfile=launchConfiguration.iamRole,
          imageId=launchConfiguration.imageId,
          instanceMonitoring=InstanceMonitoring(launchConfiguration.instanceMonitoring),
          instanceType=launchConfiguration.instanceType,
          keyName=launchConfiguration.keyPair,
          ramdiskId=launchConfiguration.ramdiskId)
      }
      LaunchInfo.LAUNCH_TEMPLATE -> {
        launchTemplate = LaunchTemplate(LaunchTemplateData(
          ebsOptimized=launchConfiguration.ebsOptimized,
          iamInstanceProfile= IamInstanceProfile(launchConfiguration.iamRole),
          imageId=launchConfiguration.imageId,
          instanceType=launchConfiguration.instanceType,
          keyName=launchConfiguration.keyPair,
          monitoring=InstanceMonitoring(launchConfiguration.instanceMonitoring),
          ramDiskId=launchConfiguration.ramdiskId))
      }
    }
    ActiveServerGroup(
      name = "$name-v$sequence",
      region = location.region,
      zones = location.availabilityZones,
      image = ActiveServerGroupImage(
        imageId = launchConfiguration.imageId,
        appVersion = launchConfiguration.appVersion,
        baseImageVersion = launchConfiguration.baseImageVersion,
        name = "name",
        imageLocation = "location",
        description = image?.description
      ),
      launchConfig = launchConfig,
      launchTemplate = launchTemplate,
      asg = AutoScalingGroup(
        "$name-v$sequence",
        health.cooldown.seconds,
        health.healthCheckType.let(HealthCheckType::toString),
        health.warmup.seconds,
        scaling.suspendedProcesses.map { SuspendedProcess(it.name) }.toSet(),
        health.enabledMetrics.map(Metric::toString).toSet(),
        tags.map { Tag(it.key, it.value) }.toSet(),
        health.terminationPolicies.map(TerminationPolicy::toString).toSet(),
        subnets.map(Subnet::id).joinToString(",")
      ),
      scalingPolicies = listOf(
        ScalingPolicy(
          autoScalingGroupName = "$name-v$sequence",
          policyName = "$name-target-tracking-policy",
          policyType = "TargetTrackingScaling",
          estimatedInstanceWarmup = 300,
          adjustmentType = null,
          minAdjustmentStep = null,
          minAdjustmentMagnitude = null,
          stepAdjustments = null,
          metricAggregationType = null,
          targetTrackingConfiguration = TargetTrackingConfiguration(
            560.0,
            true,
            CustomizedMetricSpecificationModel(
              "RPS per instance",
              "SPIN/ACH",
              "Average",
              null,
              listOf(MetricDimensionModel("AutoScalingGroupName", "$name-v$sequence"))
            ),
            null
          ),
          alarms = listOf(
            ScalingPolicyAlarm(
              true,
              "GreaterThanThreshold",
              listOf(MetricDimensionModel("AutoScalingGroupName", "$name-v$sequence")),
              3,
              60,
              560,
              "RPS per instance",
              "SPIN/ACH",
              "Average"
            )
          )
        )
      ),
      vpcId = vpc.id,
      targetGroups = dependencies.targetGroups,
      loadBalancers = dependencies.loadBalancerNames,
      capacity = capacity.let { Capacity(it.min, it.max, it.desired) },
      cloudProvider = CLOUD_PROVIDER,
      securityGroups = securityGroups.map(SecurityGroupSummary::id).toSet(),
      accountName = location.account,
      moniker = parseMoniker("$name-v$sequence"),
      instanceCounts = instanceCounts.run { InstanceCounts(total, up, down, unknown, outOfService, starting) },
      createdTime = 1544656134371
    )
  }


fun ServerGroup.toMultiServerGroupResponse(
  vpc: Network,
  subnets: List<Subnet>,
  securityGroups: List<SecurityGroupSummary>,
  image: ServerGroup.ActiveServerGroupImage? = null,
  instanceCounts: ServerGroup.InstanceCounts = ServerGroup.InstanceCounts(1, 1, 0, 0, 0, 0),
  allEnabled: Boolean = false
): Set<ClouddriverServerGroup> {
  RandomStringUtils.randomNumeric(3).let { sequence ->
    val sequence1 = sequence.padStart(3, '0')
    val sequence2 = (sequence.toInt() + 1).toString().padStart(3, '0')
    val serverGroups = mutableSetOf<ClouddriverServerGroup>()

    val first = ClouddriverServerGroup(
      name = "$name-v$sequence1",
      region = location.region,
      zones = location.availabilityZones,
      image = ActiveServerGroupImage(
        imageId = launchConfiguration.imageId,
        appVersion = launchConfiguration.appVersion,
        baseImageVersion = launchConfiguration.baseImageVersion,
        name = "name",
        imageLocation = "location",
        description = image?.description
      ),
      launchConfig = LaunchConfig(
        launchConfiguration.ramdiskId,
        launchConfiguration.ebsOptimized,
        launchConfiguration.imageId,
        launchConfiguration.instanceType,
        launchConfiguration.keyPair,
        launchConfiguration.iamRole,
        InstanceMonitoring(launchConfiguration.instanceMonitoring)
      ),
      asg = AutoScalingGroup(
        "$name-v$sequence1",
        health.cooldown.seconds,
        health.healthCheckType.let(HealthCheckType::toString),
        health.warmup.seconds,
        scaling.suspendedProcesses.map { SuspendedProcess(it.name) }.toSet(),
        health.enabledMetrics.map(Metric::toString).toSet(),
        tags.map { Tag(it.key, it.value) }.toSet(),
        health.terminationPolicies.map(TerminationPolicy::toString).toSet(),
        subnets.map(Subnet::id).joinToString(",")
      ),
      scalingPolicies = listOf(
        ScalingPolicy(
          autoScalingGroupName = "$name-v$sequence1",
          policyName = "$name-target-tracking-policy",
          policyType = "TargetTrackingScaling",
          estimatedInstanceWarmup = 300,
          adjustmentType = null,
          minAdjustmentStep = null,
          minAdjustmentMagnitude = null,
          stepAdjustments = null,
          metricAggregationType = null,
          targetTrackingConfiguration = TargetTrackingConfiguration(
            560.0,
            true,
            CustomizedMetricSpecificationModel(
              "RPS per instance",
              "SPIN/ACH",
              "Average",
              null,
              listOf(MetricDimensionModel("AutoScalingGroupName", "$name-v$sequence1"))
            ),
            null
          ),
          alarms = listOf(
            ScalingPolicyAlarm(
              true,
              "GreaterThanThreshold",
              listOf(MetricDimensionModel("AutoScalingGroupName", "$name-v$sequence1")),
              3,
              60,
              560,
              "RPS per instance",
              "SPIN/ACH",
              "Average"
            )
          )
        )
      ),
      vpcId = vpc.id,
      targetGroups = dependencies.targetGroups,
      loadBalancers = dependencies.loadBalancerNames,
      capacity = capacity.let { Capacity(it.min, it.max, it.desired) },
      cloudProvider = CLOUD_PROVIDER,
      securityGroups = securityGroups.map(SecurityGroupSummary::id).toSet(),
      moniker = parseMoniker("$name-v$sequence1"),
      instanceCounts = instanceCounts.run { InstanceCounts(total, up, down, unknown, outOfService, starting) },
      createdTime = 1544656134371,
      disabled = !allEnabled
    )
    serverGroups.add(first)

    val second = ClouddriverServerGroup(
      name = "$name-v$sequence2",
      region = location.region,
      zones = location.availabilityZones,
      image = ActiveServerGroupImage(
        imageId = launchConfiguration.imageId,
        appVersion = launchConfiguration.appVersion,
        baseImageVersion = launchConfiguration.baseImageVersion,
        name = "name",
        imageLocation = "location",
        description = image?.description
      ),
      launchConfig = LaunchConfig(
        launchConfiguration.ramdiskId,
        launchConfiguration.ebsOptimized,
        launchConfiguration.imageId,
        launchConfiguration.instanceType,
        launchConfiguration.keyPair,
        launchConfiguration.iamRole,
        InstanceMonitoring(launchConfiguration.instanceMonitoring)
      ),
      asg = AutoScalingGroup(
        "$name-v$sequence2",
        health.cooldown.seconds,
        health.healthCheckType.let(HealthCheckType::toString),
        health.warmup.seconds,
        scaling.suspendedProcesses.map { SuspendedProcess(it.name) }.toSet(),
        health.enabledMetrics.map(Metric::toString).toSet(),
        tags.map { Tag(it.key, it.value) }.toSet(),
        health.terminationPolicies.map(TerminationPolicy::toString).toSet(),
        subnets.map(Subnet::id).joinToString(",")
      ),
      scalingPolicies = listOf(
        ScalingPolicy(
          autoScalingGroupName = "$name-v$sequence2",
          policyName = "$name-target-tracking-policy",
          policyType = "TargetTrackingScaling",
          estimatedInstanceWarmup = 300,
          adjustmentType = null,
          minAdjustmentStep = null,
          minAdjustmentMagnitude = null,
          stepAdjustments = null,
          metricAggregationType = null,
          targetTrackingConfiguration = TargetTrackingConfiguration(
            560.0,
            true,
            CustomizedMetricSpecificationModel(
              "RPS per instance",
              "SPIN/ACH",
              "Average",
              null,
              listOf(MetricDimensionModel("AutoScalingGroupName", "$name-v$sequence2"))
            ),
            null
          ),
          alarms = listOf(
            ScalingPolicyAlarm(
              true,
              "GreaterThanThreshold",
              listOf(MetricDimensionModel("AutoScalingGroupName", "$name-v$sequence2")),
              3,
              60,
              560,
              "RPS per instance",
              "SPIN/ACH",
              "Average"
            )
          )
        )
      ),
      vpcId = vpc.id,
      targetGroups = dependencies.targetGroups,
      loadBalancers = dependencies.loadBalancerNames,
      capacity = capacity.let { Capacity(it.min, it.max, it.desired) },
      cloudProvider = CLOUD_PROVIDER,
      securityGroups = securityGroups.map(SecurityGroupSummary::id).toSet(),
      moniker = parseMoniker("$name-v$sequence1"),
      instanceCounts = instanceCounts.run { InstanceCounts(total, up, down, unknown, outOfService, starting) },
      createdTime = 1544656134371,
      disabled = false
    )

    serverGroups.add(second)
    return serverGroups
  }
}

fun ActiveServerGroup.toAllServerGroupsResponse(
  disabled: Boolean = false
): ClouddriverServerGroup =
  ClouddriverServerGroup(
    name = name,
    region = region,
    zones = zones,
    image = image,
    launchConfig = launchConfig,
    launchTemplate = launchTemplate,
    asg = asg,
    scalingPolicies = scalingPolicies,
    vpcId = vpcId,
    targetGroups = targetGroups,
    loadBalancers = loadBalancers,
    capacity = capacity,
    cloudProvider = cloudProvider,
    securityGroups = securityGroups,
    moniker = moniker,
    buildInfo = buildInfo,
    instanceCounts = instanceCounts,
    createdTime = createdTime,
    disabled = disabled
  )
