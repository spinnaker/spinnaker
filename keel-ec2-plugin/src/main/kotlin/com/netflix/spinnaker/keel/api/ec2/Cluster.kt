package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType.EC2
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy.OldestInstance
import java.time.Duration

@JsonInclude(NON_NULL)
data class Cluster(
  val moniker: ClusterMoniker,
  val location: ClusterLocation,
  val launchConfiguration: LaunchConfiguration,
  val capacity: Capacity = Capacity(1, 1, 1),
  val dependencies: Dependencies = Dependencies(),
  val health: Health = Health(),
  val suspendedProcesses: Set<ScalingProcess> = emptySet(),
  val tags: Map<String, String> = emptyMap()
) {

  data class LaunchConfiguration(
    val imageId: String,
    val instanceType: String,
    val ebsOptimized: Boolean,
    val iamRole: String,
    val keyPair: String,
    val instanceMonitoring: Boolean = false,
    val ramdiskId: String? = null
  )

  data class Dependencies(
    val loadBalancerNames: Set<String> = emptySet(),
    val securityGroupNames: Set<String> = emptySet(),
    val targetGroups: Set<String> = emptySet()
  )

  data class Health(
    val cooldown: Duration = Duration.ofSeconds(10),
    val warmup: Duration = Duration.ofSeconds(600),
    val healthCheckType: HealthCheckType = EC2,
    val enabledMetrics: Set<Metric> = emptySet(),
    val terminationPolicies: Set<TerminationPolicy> = setOf(OldestInstance)
  )
}

