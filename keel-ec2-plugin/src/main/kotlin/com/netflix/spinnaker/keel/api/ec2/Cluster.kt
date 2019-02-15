package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.netflix.spinnaker.keel.api.ec2.HealthCheckType.EC2
import com.netflix.spinnaker.keel.api.ec2.TerminationPolicy.OldestInstance
import java.time.Duration

@JsonInclude(NON_NULL)
data class Cluster(
  val moniker: Moniker,
  val location: Location,
  val launchConfiguration: LaunchConfiguration,
  val capacity: Capacity = Capacity(1, 1, 1),
  val dependencies: Dependencies = Dependencies(),
  val health: Health = Health(),
  val scaling: Scaling = Scaling(),
  val tags: Map<String, String> = emptyMap()
) {
  data class Moniker(
    val application: String,
    val stack: String? = null,
    val detail: String? = null
  ) {
    val cluster: String
      get() = when {
        stack == null && detail == null -> application
        detail == null -> "$application-$stack"
        else -> "$application-${stack.orEmpty()}-$detail"
      }
  }

  data class Location(
    val accountName: String,
    val region: String,
    val subnet: String?, // TODO: is this actually optional?
    val availabilityZones: Set<String>
  )

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

  data class Scaling(
    val suspendedProcesses: Set<ScalingProcess> = emptySet()
  )
}
