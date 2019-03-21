package com.netflix.spinnaker.keel.clouddriver.model

data class ClusterActiveServerGroup(
  val name: String,
  val region: String,
  val zones: Set<String>,
  val launchConfig: LaunchConfig,
  val asg: AutoScalingGroup,
  val vpcId: String,
  val targetGroups: Set<String>,
  val loadBalancers: Set<String>,
  val capacity: ServerGroupCapacity,
  val cloudProvider: String,
  val securityGroups: Set<String>,
  val accountName: String,
  val moniker: Moniker
)

data class LaunchConfig(
  val ramdiskId: String?,
  val ebsOptimized: Boolean,
  val imageId: String?,
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
  val suspendedProcesses: Set<String>,
  val enabledMetrics: Set<String>,
  val tags: Set<Tag>,
  val terminationPolicies: Set<String>,
  val vpczoneIdentifier: String
)

data class Tag(
  val key: String,
  val value: String
)

data class ServerGroupCapacity(
  val min: Int,
  val max: Int,
  val desired: Int
)

data class InstanceMonitoring(
  val enabled: Boolean
)
