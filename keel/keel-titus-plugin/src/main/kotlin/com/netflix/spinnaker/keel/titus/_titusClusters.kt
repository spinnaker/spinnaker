package com.netflix.spinnaker.keel.titus

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.Capacity.AutoScalingCapacity
import com.netflix.spinnaker.keel.api.ec2.Capacity.DefaultCapacity
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
import com.netflix.spinnaker.keel.api.ec2.DEFAULT_AUTOSCALE_SCALE_IN_COOLDOWN
import com.netflix.spinnaker.keel.api.ec2.DEFAULT_AUTOSCALE_SCALE_OUT_COOLDOWN
import com.netflix.spinnaker.keel.api.ec2.Scaling
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup.Location
import com.netflix.spinnaker.keel.clouddriver.model.Resources
import com.netflix.spinnaker.keel.core.parseMoniker
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.DigestProvider
import com.netflix.spinnaker.keel.titus.exceptions.ErrorResolvingContainerException

internal fun Iterable<TitusServerGroup>.byRegion(): Map<String, TitusServerGroup> =
  associateBy { it.location.region }

internal val TitusServerGroup.moniker: Moniker
  get() = parseMoniker(name)

internal fun TitusClusterSpec.resolveCapacity(region: String? = null): Capacity {
  val scaling = region?.let { overrides[it] }?.scaling ?: defaults.scaling
  val capacity = region?.let { overrides[it] }?.capacity ?: defaults.capacity

  return when {
    capacity == null && scaling != null -> AutoScalingCapacity(1, 1, 1)
    capacity == null -> DefaultCapacity(1, 1, 1)
    scaling != null -> AutoScalingCapacity(capacity)
    else -> DefaultCapacity(capacity)
  }
}

internal val NETFLIX_CONTAINER_ENV_VARS = arrayOf("EC2_REGION", "NETFLIX_REGION", "NETFLIX_HOME_REGION")

internal fun TitusClusterSpec.resolveEnv(region: String? = null): Map<String, String> {
  val regionalVars: Map<String, String> = region?.let { NETFLIX_CONTAINER_ENV_VARS.associateWith { region }} ?: emptyMap()
  return defaults.env + regionalVars + (region?.let { overrides[it] }?.env ?: emptyMap())
}

internal fun TitusClusterSpec.resolveContainerAttributes(region: String? = null): Map<String, String> =
  emptyMap<String, String>() +
    defaults.containerAttributes +
    (region?.let { overrides[it] }?.containerAttributes ?: emptyMap())

internal fun TitusClusterSpec.resolveResources(region: String? = null): TitusServerGroup.Resources {
  val default by lazy { Resources() }

  return TitusServerGroup.Resources(
    cpu = region?.let { overrides[it] }?.resources?.cpu
      ?: defaults.resources?.cpu
      ?: default.cpu,
    disk = region?.let { overrides[it] }?.resources?.disk
      ?: defaults.resources?.disk
      ?: default.disk,
    gpu = region?.let { overrides[it] }?.resources?.gpu
      ?: defaults.resources?.gpu
      ?: default.gpu,
    memory = region?.let { overrides[it] }?.resources?.memory
      ?: defaults.resources?.memory
      ?: default.memory,
    networkMbps = region?.let { overrides[it] }?.resources?.networkMbps
      ?: defaults.resources?.networkMbps
      ?: default.networkMbps
  )
}

internal fun TitusClusterSpec.resolveIamProfile(region: String) =
  overrides[region]?.iamProfile ?: defaults.iamProfile ?: moniker.app + "InstanceProfile"

internal fun TitusClusterSpec.resolveEntryPoint(region: String? = null) =
  when (region) {
    null -> defaults.entryPoint ?: ""
    else -> overrides[region]?.entryPoint ?: defaults.entryPoint ?: ""
  }

internal fun TitusClusterSpec.resolveCapacityGroup(region: String? = null) =
  when (region) {
    null -> defaults.capacityGroup ?: moniker.app
    else -> overrides[region]?.capacityGroup ?: defaults.capacityGroup ?: moniker.app
  }

internal fun TitusClusterSpec.resolveConstraints(region: String? = null) =
  region?.let { overrides[it] }?.constraints ?: defaults.constraints ?: TitusServerGroup.Constraints()

internal fun resolveContainerProvider(container: ContainerProvider): DigestProvider {
  if (container is DigestProvider) {
    return container
  } else {
    // The spec container should be replaced with a resolved container by now.
    // If not, something is wrong.
    throw ErrorResolvingContainerException(container)
  }
}

internal fun TitusClusterSpec.resolveMigrationPolicy(region: String? = null) =
  region?.let { overrides[it] }?.migrationPolicy ?: defaults.migrationPolicy ?: TitusServerGroup.MigrationPolicy()

internal fun TitusClusterSpec.resolveDependencies(region: String? = null): ClusterDependencies =
  ClusterDependencies(
    loadBalancerNames = defaults.dependencies?.loadBalancerNames + (region?.let { overrides[it] }?.dependencies?.loadBalancerNames ?: emptySet()),
    securityGroupNames = defaults.dependencies?.securityGroupNames + (region?.let { overrides[it] }?.dependencies?.securityGroupNames ?: emptySet()),
    targetGroups = defaults.dependencies?.targetGroups + (region?.let { overrides[it] }?.dependencies?.targetGroups ?: emptySet())
  )

fun TitusClusterSpec.resolveScaling(region: String? = null) =
  // TODO: could be smarter here and merge policies from defaults and override
  (region?.let { overrides[it] }?.scaling ?: defaults.scaling)
  ?.run {
    // we set the warmup to ZERO as Titus doesn't use the warmup setting
    Scaling(
      targetTrackingPolicies = targetTrackingPolicies.map { it.copy(warmup = null, scaleOutCooldown = it.scaleOutCooldown ?: DEFAULT_AUTOSCALE_SCALE_OUT_COOLDOWN, scaleInCooldown = it.scaleInCooldown ?: DEFAULT_AUTOSCALE_SCALE_IN_COOLDOWN) }.toSet(),
      stepScalingPolicies = stepScalingPolicies.map { it.copy(warmup = null) }.toSet()
    )
  } ?: Scaling()


fun TitusClusterSpec.resolveTags(region: String? = null) =
  defaults.tags + (region?.let { overrides[it] }?.tags ?: emptyMap())

internal fun TitusClusterSpec.resolve(): Set<TitusServerGroup> =
  locations.regions.map {
    TitusServerGroup(
      id = null,
      name = moniker.toString(),
      location = Location(
        account = locations.account,
        region = it.name
      ),
      capacity = resolveCapacity(it.name),
      capacityGroup = resolveCapacityGroup(it.name),
      constraints = resolveConstraints(it.name),
      container = resolveContainerProvider(container),
      dependencies = resolveDependencies(it.name),
      entryPoint = resolveEntryPoint(it.name),
      env = resolveEnv(it.name),
      containerAttributes = resolveContainerAttributes(it.name),
      migrationPolicy = resolveMigrationPolicy(it.name),
      resources = resolveResources(it.name),
      tags = resolveTags(it.name),
      artifactName = artifactName,
      artifactVersion = artifactVersion,
      scaling = resolveScaling(it.name)
    )
  }
    .toSet()

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
