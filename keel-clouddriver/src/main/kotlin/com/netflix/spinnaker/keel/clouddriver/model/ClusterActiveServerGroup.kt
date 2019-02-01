package com.netflix.spinnaker.keel.clouddriver.model

data class ClusterActiveServerGroup(
  val name: String,
  val region: String,
  val zones: Collection<String>,
  val launchConfig: LaunchConfig,
  val asg: AutoScalingGroup,
  val vpcId: String,
  val targetGroups: Collection<String>,
  val loadBalancers: Collection<String>,
  val capacity: ServerGroupCapacity,
  val securityGroups: Collection<String>,
  val accountName: String,
  val moniker: Moniker
)

data class LaunchConfig(
  val ramdiskId: String?,
  val ebsOptimized: Boolean,
  val imageId: String,
  val userData: String?,
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
  val suspendedProcesses: Collection<String>,
  val enabledMetrics: Collection<String>,
  val tags: Collection<Tag>,
  val terminationPolicies: Collection<String>
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
