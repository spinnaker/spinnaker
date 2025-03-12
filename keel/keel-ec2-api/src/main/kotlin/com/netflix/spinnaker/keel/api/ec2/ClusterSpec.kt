package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.ClusterDeployStrategy
import com.netflix.spinnaker.keel.api.ComputeResourceSpec
import com.netflix.spinnaker.keel.api.Dependency
import com.netflix.spinnaker.keel.api.DependencyType.LOAD_BALANCER
import com.netflix.spinnaker.keel.api.DependencyType.SECURITY_GROUP
import com.netflix.spinnaker.keel.api.DependencyType.TARGET_GROUP
import com.netflix.spinnaker.keel.api.Dependent
import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.ManagedRolloutConfig
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.Health
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.LaunchConfiguration
import com.netflix.spinnaker.keel.api.schema.Factory
import com.netflix.spinnaker.keel.api.schema.Optional
import java.time.Duration

/**
 * Transforms a [ClusterSpec] into a concrete model of server group desired states.
 */
fun ClusterSpec.resolve(): Set<ServerGroup> =
  locations.regions.map {
    ServerGroup(
      name = moniker.toString(),
      location = Location(
        account = locations.account,
        region = it.name,
        vpc = locations.vpc ?: error("No vpc supplied or resolved"),
        subnet = locations.subnet ?: error("No subnet purpose supplied or resolved"),
        availabilityZones = it.availabilityZones
      ),
      launchConfiguration = resolveLaunchConfiguration(it),
      capacity = resolveCapacity(it.name),
      dependencies = resolveDependencies(it.name),
      health = resolveHealth(it.name),
      scaling = resolveScaling(it.name),
      tags = resolveTags(it.name),
      artifactName = artifactName,
      artifactVersion = artifactVersion
    )
  }
    .toSet()

fun ClusterSpec.resolveTags(region: String? = null) =
  defaults.tags + (region?.let { overrides[it] }?.tags ?: emptyMap())

private fun ClusterSpec.resolveLaunchConfiguration(region: SubnetAwareRegionSpec): LaunchConfiguration {
  val image = checkNotNull(
    overrides[region.name]?.launchConfiguration?.image
      ?: defaults.launchConfiguration?.image
  ) { "No image resolved / specified for ${region.name}" }
  return LaunchConfiguration(
    appVersion = image.appVersion,
    baseImageName = image.baseImageName,
    imageId = image.id,
    instanceType = checkNotNull(
      overrides[region.name]?.launchConfiguration?.instanceType
        ?: defaults.launchConfiguration?.instanceType
    ) {
      "No instance type resolved for $id (region ${region.name}) and cannot determine a default"
    },
    ebsOptimized = checkNotNull(
      overrides[region.name]?.launchConfiguration?.ebsOptimized
        ?: defaults.launchConfiguration?.ebsOptimized
        ?: LaunchConfiguration.DEFAULT_EBS_OPTIMIZED
    ),
    iamRole = checkNotNull(
      overrides[region.name]?.launchConfiguration?.iamRole
        ?: defaults.launchConfiguration?.iamRole
        ?: LaunchConfiguration.defaultIamRoleFor(moniker.app)
    ),
    keyPair = checkNotNull(
      overrides[region.name]?.launchConfiguration?.keyPair
        ?: defaults.launchConfiguration?.keyPair
    ) {
      "No keypair resolved for $id (region ${region.name}) and cannot determine a default"
    },
    instanceMonitoring = overrides[region.name]?.launchConfiguration?.instanceMonitoring
      ?: defaults.launchConfiguration?.instanceMonitoring
      ?: LaunchConfiguration.DEFAULT_INSTANCE_MONITORING,
    ramdiskId = overrides[region.name]?.launchConfiguration?.ramdiskId
      ?: defaults.launchConfiguration?.ramdiskId,
    requireIMDSv2 = (overrides[region.name]?.launchConfiguration?.instanceMetadataServiceVersion
      ?: defaults.launchConfiguration?.instanceMetadataServiceVersion) == InstanceMetadataServiceVersion.V2
  )
}

fun ClusterSpec.resolveCapacity(region: String? = null): Capacity =
  when (region) {
    null -> defaults.resolveCapacity() ?: Capacity.DefaultCapacity(1, 1, 1)
    else -> overrides[region]?.resolveCapacity() ?: defaults.resolveCapacity() ?: Capacity.DefaultCapacity(1, 1, 1)
  }

fun ServerGroupSpec.resolveCapacity(): Capacity? =
  when {
    capacity == null -> null
    scaling.hasScalingPolicies() -> Capacity.AutoScalingCapacity(capacity)
    else -> Capacity.DefaultCapacity(capacity)
  }

fun ClusterSpec.resolveScaling(region: String? = null): Scaling =
  Scaling(
    suspendedProcesses = defaults.scaling?.suspendedProcesses +
      (region?.let { overrides[it] }?.scaling?.suspendedProcesses ?: emptySet()),
    targetTrackingPolicies = defaults.scaling?.targetTrackingPolicies +
      (region?.let { overrides[it] }?.scaling?.targetTrackingPolicies ?: emptySet()),
    stepScalingPolicies = defaults.scaling?.stepScalingPolicies +
      (region?.let { overrides[it] }?.scaling?.stepScalingPolicies ?: emptySet())
  )

fun ClusterSpec.resolveDependencies(region: String? = null): ClusterDependencies =
  ClusterDependencies(
    loadBalancerNames = defaults.dependencies?.loadBalancerNames +
      (region?.let { overrides[it] }?.dependencies?.loadBalancerNames ?: emptySet()),
    securityGroupNames = defaults.dependencies?.securityGroupNames +
      (region?.let { overrides[it] }?.dependencies?.securityGroupNames ?: emptySet()),
    targetGroups = defaults.dependencies?.targetGroups +
      (region?.let { overrides[it] }?.dependencies?.targetGroups ?: emptySet())
  )

fun ClusterSpec.resolveHealth(region: String? = null): Health {
  val default by lazy { Health() }
  return Health(
    cooldown = region?.let { overrides[it] }?.health?.cooldown
      ?: defaults.health?.cooldown
      ?: default.cooldown,
    warmup = region?.let { overrides[it] }?.health?.warmup
      ?: defaults.health?.warmup
      ?: default.warmup,
    healthCheckType = region?.let { overrides[it] }?.health?.healthCheckType
      ?: defaults.health?.healthCheckType
      ?: default.healthCheckType,
    enabledMetrics = region?.let { overrides[it] }?.health?.enabledMetrics
      ?: defaults.health?.enabledMetrics
      ?: default.enabledMetrics,
    terminationPolicies = region?.let { overrides[it] }?.health?.terminationPolicies
      ?: defaults.health?.terminationPolicies
      ?: default.terminationPolicies
  )
}

data class ClusterSpec(
  override val moniker: Moniker,
  override val artifactReference: String? = null,
  val deployWith: ClusterDeployStrategy = RedBlack(),
  val managedRollout: ManagedRolloutConfig = ManagedRolloutConfig(),
  override val locations: SubnetAwareLocations,
  private val _defaults: ServerGroupSpec,
  override val overrides: Map<String, ServerGroupSpec> = emptyMap(),
  override val artifactName: String? = null,
  override val artifactVersion: String? = null
) : ComputeResourceSpec<SubnetAwareLocations>, OverrideableClusterDependencyContainer<ServerGroupSpec>, Dependent {
  @Factory
  constructor(
    moniker: Moniker,
    artifactReference: String? = null,
    deployWith: ClusterDeployStrategy = RedBlack(),
    @Optional locations: SubnetAwareLocations,
    launchConfiguration: LaunchConfigurationSpec? = null,
    capacity: CapacitySpec? = null,
    dependencies: ClusterDependencies? = null,
    health: HealthSpec? = null,
    scaling: Scaling? = null,
    tags: Map<String, String>? = null,
    overrides: Map<String, ServerGroupSpec> = emptyMap(),
    managedRollout: ManagedRolloutConfig = ManagedRolloutConfig()
  ) : this(
    moniker,
    artifactReference,
    deployWith,
    managedRollout,
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

  override val id = "${locations.account}:$moniker"

  /**
   * I have no idea why, but if I annotate the constructor property with @get:JsonUnwrapped, the
   * @JsonCreator constructor below nulls out everything in the ClusterServerGroupSpec some time
   * very late in parsing. Using a debugger I can see it assigning the object correctly but then it
   * seems to overwrite it. This is a bit nasty but I think having the cluster-wide defaults at the
   * top level in the cluster spec YAML / JSON is nicer for the user.
   */
  override val defaults: ServerGroupSpec
    get() = _defaults

  override val artifactType: ArtifactType = DEBIAN

  @get:ExcludedFromDiff
  override val dependsOn: Set<Dependency>
    get() = locations.regions.flatMap { region ->
      val deps = mutableListOf<Dependency>()
      _defaults.dependencies?.loadBalancerNames?.forEach {
        deps.add(Dependency(LOAD_BALANCER, region.name, it))
      }
      _defaults.dependencies?.securityGroupNames?.forEach {
        deps.add(Dependency(SECURITY_GROUP, region.name, it))
      }
      _defaults.dependencies?.targetGroups?.forEach {
        deps.add(Dependency(TARGET_GROUP, region.name, it))
      }
      overrides[region.name]?.dependencies?.loadBalancerNames?.forEach {
        deps.add(Dependency(LOAD_BALANCER, region.name, it))
      }
      overrides[region.name]?.dependencies?.securityGroupNames?.forEach {
        deps.add(Dependency(SECURITY_GROUP, region.name, it))
      }
      overrides[region.name]?.dependencies?.targetGroups?.forEach {
        deps.add(Dependency(TARGET_GROUP, region.name, it))
      }
      deps
    }.toSet()

  override fun deepRename(suffix: String) =
    copy(moniker = moniker.withSuffix(suffix))

  data class ServerGroupSpec(
    val launchConfiguration: LaunchConfigurationSpec? = null,
    val capacity: CapacitySpec? = null,
    override val dependencies: ClusterDependencies? = null,
    val health: HealthSpec? = null,
    val scaling: Scaling? = null,
    val tags: Map<String, String>? = null
  ) : ClusterDependencyContainer {
    init {
      // Require capacity.desired or scaling policies, or let them both be blank for constructing overrides
      require(!(capacity?.desired != null && scaling.hasScalingPolicies())) {
        "capacity.desired and auto-scaling policies are mutually exclusive: current = $capacity, $scaling"
      }
    }
  }

  /**
   * Capacity definition with an optional [desired] which _must_ be `null` if the server group has scaling policies.
   */
  data class CapacitySpec(
    val min: Int,
    val max: Int,
    val desired: Int? = null
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
  regions.first { it.name == region }

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
