package com.netflix.spinnaker.keel.titus

import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.ec2.Capacity
import com.netflix.spinnaker.keel.api.ec2.ClusterDependencies
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

internal fun TitusClusterSpec.resolveCapacity(region: String) =
  overrides[region]?.capacity ?: defaults.capacity ?: Capacity(1, 1, 1)

internal fun TitusClusterSpec.resolveEnv(region: String) =
  emptyMap<String, String>() + defaults.env + overrides[region]?.env

internal fun TitusClusterSpec.resolveContainerAttributes(region: String) =
  emptyMap<String, String>() + defaults.containerAttributes + overrides[region]?.containerAttributes

internal fun TitusClusterSpec.resolveResources(region: String): TitusServerGroup.Resources {
  val default by lazy { Resources() }
  return TitusServerGroup.Resources(
    cpu = overrides[region]?.resources?.cpu ?: defaults.resources?.cpu ?: default.cpu,
    disk = overrides[region]?.resources?.disk ?: defaults.resources?.disk ?: default.disk,
    gpu = overrides[region]?.resources?.gpu ?: defaults.resources?.gpu ?: default.gpu,
    memory = overrides[region]?.resources?.memory ?: defaults.resources?.memory ?: default.memory,
    networkMbps = overrides[region]?.resources?.networkMbps ?: defaults.resources?.networkMbps
    ?: default.networkMbps
  )
}

internal fun TitusClusterSpec.resolveIamProfile(region: String) =
  overrides[region]?.iamProfile ?: defaults.iamProfile ?: moniker.app + "InstanceProfile"

internal fun TitusClusterSpec.resolveEntryPoint(region: String) =
  overrides[region]?.entryPoint ?: defaults.entryPoint ?: ""

internal fun TitusClusterSpec.resolveCapacityGroup(region: String) =
  overrides[region]?.capacityGroup ?: defaults.capacityGroup ?: moniker.app

internal fun TitusClusterSpec.resolveConstraints(region: String) =
  overrides[region]?.constraints ?: defaults.constraints ?: TitusServerGroup.Constraints()

internal fun resolveContainerProvider(container: ContainerProvider): DigestProvider {
  if (container is DigestProvider) {
    return container
  } else {
    // The spec container should be replaced with a resolved container by now.
    // If not, something is wrong.
    throw ErrorResolvingContainerException(container)
  }
}

internal fun TitusClusterSpec.resolveMigrationPolicy(region: String) =
  overrides[region]?.migrationPolicy ?: defaults.migrationPolicy
  ?: TitusServerGroup.MigrationPolicy()

internal fun TitusClusterSpec.resolveDependencies(region: String): ClusterDependencies =
  ClusterDependencies(
    loadBalancerNames = defaults.dependencies?.loadBalancerNames + overrides[region]?.dependencies?.loadBalancerNames,
    securityGroupNames = defaults.dependencies?.securityGroupNames + overrides[region]?.dependencies?.securityGroupNames,
    targetGroups = defaults.dependencies?.targetGroups + overrides[region]?.dependencies?.targetGroups
  )

internal fun TitusClusterSpec.resolve(): Set<TitusServerGroup> =
  locations.regions.map {
    TitusServerGroup(
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
      iamProfile = resolveIamProfile(it.name),
      migrationPolicy = resolveMigrationPolicy(it.name),
      resources = resolveResources(it.name),
      tags = defaults.tags + overrides[it.name]?.tags,
      artifactName = artifactName,
      artifactVersion = artifactVersion
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
