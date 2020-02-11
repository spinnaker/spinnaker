package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.ec2.HealthCheckType
import com.netflix.spinnaker.keel.api.ec2.Metric
import com.netflix.spinnaker.keel.api.ec2.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroup
import com.netflix.spinnaker.keel.clouddriver.model.ActiveServerGroupImage
import com.netflix.spinnaker.keel.clouddriver.model.AutoScalingGroup
import com.netflix.spinnaker.keel.clouddriver.model.CustomizedMetricSpecificationModel
import com.netflix.spinnaker.keel.clouddriver.model.InstanceMonitoring
import com.netflix.spinnaker.keel.clouddriver.model.LaunchConfig
import com.netflix.spinnaker.keel.clouddriver.model.MetricDimensionModel
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.ScalingPolicy
import com.netflix.spinnaker.keel.clouddriver.model.ScalingPolicyAlarm
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.keel.clouddriver.model.Subnet
import com.netflix.spinnaker.keel.clouddriver.model.SuspendedProcess
import com.netflix.spinnaker.keel.clouddriver.model.Tag
import com.netflix.spinnaker.keel.clouddriver.model.TargetTrackingConfiguration
import com.netflix.spinnaker.keel.core.api.Capacity
import com.netflix.spinnaker.keel.core.parseMoniker
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import org.apache.commons.lang3.RandomStringUtils

fun ServerGroup.toCloudDriverResponse(
  vpc: Network,
  subnets: List<Subnet>,
  securityGroups: List<SecurityGroupSummary>
): ActiveServerGroup =
  RandomStringUtils.randomNumeric(3).padStart(3, '0').let { sequence ->
    ActiveServerGroup(
      "$name-v$sequence",
      location.region,
      location.availabilityZones,
      ActiveServerGroupImage(
        launchConfiguration.imageId,
        launchConfiguration.appVersion,
        launchConfiguration.baseImageVersion
      ),
      LaunchConfig(
        launchConfiguration.ramdiskId,
        launchConfiguration.ebsOptimized,
        launchConfiguration.imageId,
        launchConfiguration.instanceType,
        launchConfiguration.keyPair,
        launchConfiguration.iamRole,
        InstanceMonitoring(launchConfiguration.instanceMonitoring)
      ),
      AutoScalingGroup(
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
      listOf(ScalingPolicy(
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
        alarms = listOf(ScalingPolicyAlarm(
          true,
          "GreaterThanThreshold",
          listOf(MetricDimensionModel("AutoScalingGroupName", "$name-v$sequence")),
          3,
          60,
          560,
          "RPS per instance",
          "SPIN/ACH",
          "Average"
        ))
      )),
      vpc.id,
      dependencies.targetGroups,
      dependencies.loadBalancerNames,
      capacity.let { Capacity(it.min, it.max, it.desired) },
      CLOUD_PROVIDER,
      securityGroups.map(SecurityGroupSummary::id).toSet(),
      location.account,
      parseMoniker("$name-v$sequence")
    )
  }
