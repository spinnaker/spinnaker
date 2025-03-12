package com.netflix.spinnaker.keel.api.ec2

import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SubnetAwareLocations

/**
 * A [ResourceSpec] that has default and override cluster dependencies (i.e. security groups, load
 * balancers, and/or target groups).
 */
interface OverrideableClusterDependencyContainer<T : ClusterDependencyContainer> :
  Locatable<SubnetAwareLocations> {
  val defaults: T

  /**
   * Overrides by region. Map keys are region names.
   */
  val overrides: Map<String, T>
}

interface ClusterDependencyContainer {
  val dependencies: ClusterDependencies?
}

/**
 * A named dependency (security group, load balancer, or target group) that must be present in one or more regions.
 */
data class RegionalDependency(
  val name: String,
  val account: String,
  val regions: Set<String>
)

/**
 * Resolves overrides and returns a map of security group name to the regions it is required in.
 * For example:
 * ```
 * fnord -> (us-east-1, us-west-2)
 * fnord-elb -> (us-east-1, us-west-2)
 * fnord-ext -> (us-east-1)
 * ```
 */
val OverrideableClusterDependencyContainer<*>.securityGroupsByRegion: Collection<RegionalDependency>
  get() = dependencyByRegion { it.securityGroupNames }

val OverrideableClusterDependencyContainer<*>.loadBalancersByRegion: Collection<RegionalDependency>
  get() = dependencyByRegion { it.loadBalancerNames }

val OverrideableClusterDependencyContainer<*>.targetGroupsByRegion: Collection<RegionalDependency>
  get() = dependencyByRegion { it.targetGroups }

private fun OverrideableClusterDependencyContainer<*>.dependencyByRegion(fn: (ClusterDependencies) -> Set<String>): Collection<RegionalDependency> {
  val regions = locations.regions.map { it.name }.toSet()
  val defaults = (defaults.dependencies?.let(fn) ?: emptySet())
    .associateWith { regions }
  val overrides = overrides
    .map { (region, spec) ->
      spec.dependencies?.let(fn)?.associateWith { setOf(region) } ?: emptyMap()
    }
    .merge()
  return listOf(defaults, overrides)
    .merge()
    .map { (k, v) ->
      RegionalDependency(k, locations.account, v)
    }
}

/**
 * Reduces a collection of `Map<*, Set>` down to a single map where the values contain the combined
 * set of values from all elements in the original collection with a common key.
 */
private fun <K, VE> Collection<Map<K, Set<VE>>>.merge(): Map<K, Set<VE>> =
  fold(emptyMap()) { acc, map ->
    val result = mutableMapOf<K, Set<VE>>()
    (acc.keys + map.keys).forEach {
      result[it] = (acc[it] ?: emptySet()) + (map[it] ?: emptySet())
    }
    result
  }
