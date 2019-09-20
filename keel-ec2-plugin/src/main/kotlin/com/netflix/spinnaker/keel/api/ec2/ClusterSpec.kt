package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.model.Moniker
import java.time.Duration

fun ClusterSpec.resolve(): Set<ServerGroupSpec> =
  locations.regions.map {
    ServerGroupSpec(
      moniker = moniker,
      location = Location(
        locations.accountName,
        it.region,
        it.subnet,
        it.availabilityZones
      ),
      launchConfiguration = resolveLaunchConfiguration(it.region),
      capacity = overrides[it.region]?.capacity ?: defaults.capacity ?: Capacity(1, 1, 1),
      dependencies = resolveDependencies(it.region),
      health = resolveHealth(it.region),
      scaling = resolveScaling(it.region),
      tags = defaults.tags + overrides[it.region]?.tags
    )
  }
    .toSet()

private fun ClusterSpec.resolveLaunchConfiguration(region: String): LaunchConfigurationSpec =
  LaunchConfigurationSpec(
    imageProvider = imageProvider,
    instanceType = checkNotNull(overrides[region]?.launchConfiguration?.instanceType
      ?: defaults.launchConfiguration?.instanceType),
    ebsOptimized = checkNotNull(overrides[region]?.launchConfiguration?.ebsOptimized
      ?: defaults.launchConfiguration?.ebsOptimized),
    iamRole = checkNotNull(overrides[region]?.launchConfiguration?.iamRole
      ?: defaults.launchConfiguration?.iamRole),
    keyPair = checkNotNull(overrides[region]?.launchConfiguration?.keyPair
      ?: defaults.launchConfiguration?.keyPair),
    instanceMonitoring = overrides[region]?.launchConfiguration?.instanceMonitoring
      ?: defaults.launchConfiguration?.instanceMonitoring ?: false,
    ramdiskId = overrides[region]?.launchConfiguration?.ramdiskId
      ?: defaults.launchConfiguration?.ramdiskId
  )

private fun ClusterSpec.resolveScaling(region: String): Scaling =
  Scaling(
    suspendedProcesses = defaults.scaling?.suspendedProcesses + overrides[region]?.scaling?.suspendedProcesses
  )

private fun ClusterSpec.resolveDependencies(region: String): Dependencies =
  Dependencies(
    loadBalancerNames = defaults.dependencies?.loadBalancerNames + overrides[region]?.dependencies?.loadBalancerNames,
    securityGroupNames = defaults.dependencies?.securityGroupNames + overrides[region]?.dependencies?.securityGroupNames,
    targetGroups = defaults.dependencies?.targetGroups + overrides[region]?.dependencies?.targetGroups
  )

private fun ClusterSpec.resolveHealth(region: String): Health {
  val default by lazy { Health() }
  return Health(
    cooldown = overrides[region]?.health?.cooldown ?: defaults.health?.cooldown ?: default.cooldown,
    warmup = overrides[region]?.health?.warmup ?: defaults.health?.warmup ?: default.warmup,
    healthCheckType = overrides[region]?.health?.healthCheckType ?: defaults.health?.healthCheckType
    ?: default.healthCheckType,
    enabledMetrics = overrides[region]?.health?.enabledMetrics ?: defaults.health?.enabledMetrics
    ?: default.enabledMetrics,
    terminationPolicies = overrides[region]?.health?.terminationPolicies
      ?: defaults.health?.terminationPolicies ?: default.terminationPolicies
  )
}

data class ClusterSpec(
  override val moniker: Moniker,
  val imageProvider: ImageProvider,
  val locations: ClusterLocations,
  private val _defaults: ClusterServerGroupSpec,
  val overrides: Map<String, ClusterServerGroupSpec> = emptyMap()
) : Monikered, ResourceSpec {
  @JsonIgnore
  override val id = "${locations.accountName}:${moniker.name}"

  /*
   * I have no idea why, but if I annotate the constructor property with @get:JsonUnwrapped, the
   * @JsonCreator constructor below nulls out everything in the ClusterServerGroupSpec some time
   * very late in parsing. Using a debugger I can see it assigning the object correctly but then it
   * seems to overwrite it. This is a bit nasty but I think having the cluster-wide defaults at the
   * top level in the cluster spec YAML / JSON is nicer for the user.
   */
  val defaults: ClusterServerGroupSpec
    @JsonUnwrapped get() = _defaults

  @JsonCreator
  constructor(
    moniker: Moniker,
    imageProvider: ImageProvider,
    locations: ClusterLocations,
    launchConfiguration: ClusterLaunchConfigurationSpec?,
    capacity: Capacity?,
    dependencies: Dependencies?,
    health: ClusterHealthSpec?,
    scaling: Scaling?,
    tags: Map<String, String>?,
    overrides: Map<String, ClusterServerGroupSpec>
  ) : this(
    moniker,
    imageProvider,
    locations,
    ClusterServerGroupSpec(
      launchConfiguration,
      capacity,
      dependencies,
      health,
      scaling,
      tags
    ),
    overrides
  )
}

data class ClusterLocations(
  val accountName: String,
  val regions: Set<ClusterRegion>
)

data class ClusterRegion(
  val region: String,
  val subnet: String,
  val availabilityZones: Set<String>
)

data class ClusterServerGroupSpec(
  val launchConfiguration: ClusterLaunchConfigurationSpec? = null,
  val capacity: Capacity? = null,
  val dependencies: Dependencies? = null,
  val health: ClusterHealthSpec? = null,
  val scaling: Scaling? = null,
  val tags: Map<String, String>? = null
)

data class ClusterLaunchConfigurationSpec(
  val instanceType: String? = null,
  val ebsOptimized: Boolean? = null,
  val iamRole: String? = null,
  val keyPair: String? = null,
  val instanceMonitoring: Boolean? = null,
  val ramdiskId: String? = null
)

data class ClusterHealthSpec(
  val cooldown: Duration? = null,
  val warmup: Duration? = null,
  val healthCheckType: HealthCheckType? = null,
  val enabledMetrics: Set<Metric>? = null,
  val terminationPolicies: Set<TerminationPolicy>? = null
)

private operator fun <E> Set<E>?.plus(elements: Set<E>?): Set<E> =
  when {
    this == null || isEmpty() -> elements ?: emptySet()
    elements == null || elements.isEmpty() -> this
    else -> mutableSetOf<E>().also {
      it.addAll(this)
      it.addAll(elements)
    }
  }

private operator fun <K, V> Map<K, V>?.plus(map: Map<K, V>?): Map<K, V> =
  when {
    this == null || isEmpty() -> map ?: emptyMap()
    map == null || map.isEmpty() -> this
    else -> mutableMapOf<K, V>().also {
      it.putAll(this)
      it.putAll(map)
    }
  }
