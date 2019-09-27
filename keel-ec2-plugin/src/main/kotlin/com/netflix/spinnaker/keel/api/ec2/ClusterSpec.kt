package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.ec2.resource.ResolvedImages
import com.netflix.spinnaker.keel.model.Moniker
import java.time.Duration

/**
 * Transforms a [ClusterSpec] into a concrete model of server group desired states.
 */
fun ClusterSpec.resolve(resolvedImages: ResolvedImages): Set<ServerGroup> {
  return locations.regions.map {
    ServerGroup(
      name = moniker.name,
      location = Location(
        locations.accountName,
        it.region,
        it.subnet,
        it.availabilityZones
      ),
      launchConfiguration = resolveLaunchConfiguration(
        it.region,
        resolvedImages.appVersion,
        checkNotNull(resolvedImages.imagesByRegion[it.region]) { "No image resolved for ${it.region}" }
      ),
      capacity = resolveCapacity(it.region),
      dependencies = resolveDependencies(it.region),
      health = resolveHealth(it.region),
      scaling = resolveScaling(it.region),
      tags = defaults.tags + overrides[it.region]?.tags
    )
  }
    .toSet()
}

private fun ClusterSpec.resolveLaunchConfiguration(region: String, appVersion: String, imageId: String): LaunchConfiguration =
  LaunchConfiguration(
    appVersion = appVersion,
    imageId = imageId,
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

internal fun ClusterSpec.resolveCapacity(region: String) =
  overrides[region]?.capacity ?: defaults.capacity ?: Capacity(1, 1, 1)

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
  val locations: Locations,
  private val _defaults: ServerGroupSpec,
  val overrides: Map<String, ServerGroupSpec> = emptyMap()
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
  val defaults: ServerGroupSpec
    @JsonUnwrapped get() = _defaults

  @JsonCreator
  constructor(
    moniker: Moniker,
    imageProvider: ImageProvider,
    locations: Locations,
    launchConfiguration: LaunchConfigurationSpec?,
    capacity: Capacity?,
    dependencies: Dependencies?,
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

  data class Locations(
    val accountName: String,
    val regions: Set<ClusterRegion>
  )

  data class ClusterRegion(
    val region: String,
    val subnet: String,
    val availabilityZones: Set<String>
  )

  data class ServerGroupSpec(
    val launchConfiguration: LaunchConfigurationSpec? = null,
    val capacity: Capacity? = null,
    val dependencies: Dependencies? = null,
    val health: HealthSpec? = null,
    val scaling: Scaling? = null,
    val tags: Map<String, String>? = null
  )

  data class LaunchConfigurationSpec(
    val instanceType: String? = null,
    val ebsOptimized: Boolean? = null,
    val iamRole: String? = null,
    val keyPair: String? = null,
    val instanceMonitoring: Boolean? = null,
    val ramdiskId: String? = null
  )

  data class HealthSpec(
    val cooldown: Duration? = null,
    val warmup: Duration? = null,
    val healthCheckType: HealthCheckType? = null,
    val enabledMetrics: Set<Metric>? = null,
    val terminationPolicies: Set<TerminationPolicy>? = null
  )
}

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
