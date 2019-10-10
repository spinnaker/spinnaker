package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.MultiRegion
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.SubnetAwareRegionSpec
import java.time.Duration

/**
 * Transforms a [ClusterSpec] into a concrete model of server group desired states.
 */
fun ClusterSpec.resolve(): Set<ServerGroup> =
  locations.regions.map {
    ServerGroup(
      name = moniker.name,
      location = Location(
        locations.accountName,
        it.region,
        it.subnet,
        it.availabilityZones
      ),
      launchConfiguration = resolveLaunchConfiguration(it),
      capacity = resolveCapacity(it.region),
      dependencies = resolveDependencies(it.region),
      health = resolveHealth(it.region),
      scaling = resolveScaling(it.region),
      tags = defaults.tags + overrides[it.region]?.tags
    )
  }
    .toSet()

private fun ClusterSpec.resolveLaunchConfiguration(region: SubnetAwareRegionSpec): LaunchConfiguration {
  val image = checkNotNull(overrides[region.region]?.launchConfiguration?.image
    ?: defaults.launchConfiguration?.image) { "No image resolved / specified for ${region.region}" }
  return LaunchConfiguration(
    appVersion = image.appVersion,
    imageId = image.id,
    instanceType = checkNotNull(overrides[region.region]?.launchConfiguration?.instanceType
      ?: defaults.launchConfiguration?.instanceType),
    ebsOptimized = checkNotNull(overrides[region.region]?.launchConfiguration?.ebsOptimized
      ?: defaults.launchConfiguration?.ebsOptimized),
    iamRole = checkNotNull(overrides[region.region]?.launchConfiguration?.iamRole
      ?: defaults.launchConfiguration?.iamRole),
    keyPair = checkNotNull(overrides[region.region]?.launchConfiguration?.keyPair
      ?: defaults.launchConfiguration?.keyPair),
    instanceMonitoring = overrides[region.region]?.launchConfiguration?.instanceMonitoring
      ?: defaults.launchConfiguration?.instanceMonitoring ?: false,
    ramdiskId = overrides[region.region]?.launchConfiguration?.ramdiskId
      ?: defaults.launchConfiguration?.ramdiskId
  )
}

internal fun ClusterSpec.resolveCapacity(region: String) =
  overrides[region]?.capacity ?: defaults.capacity ?: Capacity(1, 1, 1)

private fun ClusterSpec.resolveScaling(region: String): Scaling =
  Scaling(
    suspendedProcesses = defaults.scaling?.suspendedProcesses + overrides[region]?.scaling?.suspendedProcesses
  )

private fun ClusterSpec.resolveDependencies(region: String): ClusterDependencies =
  ClusterDependencies(
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
  val imageProvider: ImageProvider? = null,
  override val locations: Locations<SubnetAwareRegionSpec>,
  private val _defaults: ServerGroupSpec,
  val overrides: Map<String, ServerGroupSpec> = emptyMap()
) : MultiRegion, Locatable<SubnetAwareRegionSpec> {
  @JsonIgnore
  override val id = "${locations.accountName}:${moniker.name}"

  override val regionalIds = locations.regions.map { clusterRegion ->
    "${locations.accountName}:${clusterRegion.region}:${moniker.name}"
  }.sorted()

  /**
   * I have no idea why, but if I annotate the constructor property with @get:JsonUnwrapped, the
   * @JsonCreator constructor below nulls out everything in the ClusterServerGroupSpec some time
   * very late in parsing. Using a debugger I can see it assigning the object correctly but then it
   * seems to overwrite it. This is a bit nasty but I think having the cluster-wide defaults at the
   * top level in the cluster spec YAML / JSON is nicer for the user.
   */
  val defaults: ServerGroupSpec
    @JsonUnwrapped get() = _defaults

  @JsonCreator
  constructor(
    moniker: Moniker,
    imageProvider: ImageProvider,
    locations: Locations<SubnetAwareRegionSpec>,
    launchConfiguration: LaunchConfigurationSpec?,
    capacity: Capacity?,
    dependencies: ClusterDependencies?,
    health: HealthSpec?,
    scaling: Scaling?,
    tags: Map<String, String>?,
    overrides: Map<String, ServerGroupSpec> = emptyMap()
  ) : this(
    moniker,
    imageProvider,
    locations,
    ServerGroupSpec(
      launchConfiguration,
      capacity,
      dependencies,
      health,
      scaling,
      tags
    ),
    overrides
  )

  data class ServerGroupSpec(
    val launchConfiguration: LaunchConfigurationSpec? = null,
    val capacity: Capacity? = null,
    val dependencies: ClusterDependencies? = null,
    val health: HealthSpec? = null,
    val scaling: Scaling? = null,
    val tags: Map<String, String>? = null
  )

  data class LaunchConfigurationSpec(
    val image: VirtualMachineImage? = null,
    val instanceType: String? = null,
    val ebsOptimized: Boolean? = null,
    val iamRole: String? = null,
    val keyPair: String? = null,
    val instanceMonitoring: Boolean? = null,
    val ramdiskId: String? = null
  )

  data class VirtualMachineImage(
    val id: String,
    val appVersion: String
  )

  data class HealthSpec(
    val cooldown: Duration? = null,
    val warmup: Duration? = null,
    val healthCheckType: HealthCheckType? = null,
    val enabledMetrics: Set<Metric>? = null,
    val terminationPolicies: Set<TerminationPolicy>? = null
  )
}

operator fun Locations<SubnetAwareRegionSpec>.get(region: String) =
  regions.first { it.region == region }

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
