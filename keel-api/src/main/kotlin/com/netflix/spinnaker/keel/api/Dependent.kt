package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.DependencyType.GENERIC_RESOURCE

/**
 * Common interface for [ResourceSpec]s that support dependencies.
 */
interface Dependent: ResourceSpec {
  /**
   * This resource's dependencies represented as a set of [Dependency].
   */
  val dependsOn: Set<Dependency>
}

/**
 * Simple representation of a resource dependency.
 *
 * TODO: replace usage of [DependencyType] with [ResourceDependency] for all resource types. This
 *  is currently precluded by modeling load balancer dependencies without a kind (ALB, CLB, etc.).
 */
open class Dependency(
  open val type: DependencyType,
  open val region: String,
  open val name: String
) {
  open fun copy(type: DependencyType = this.type, region: String = this.region, name: String = this.name): Dependency =
    Dependency(type, region, name)

  override fun equals(other: Any?): Boolean =
    other is Dependency && other.type == type && other.region == region && other.name == name

  override fun hashCode(): Int {
    var result = type.hashCode()
    result = 31 * result + region.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }

  override fun toString(): String = "Dependency(type = $type, region = $region, name = $name)"
}

/**
 * A resource dependency identified by its [ResourceKind].
 */
data class ResourceDependency(
  override val type: DependencyType = GENERIC_RESOURCE,
  override val region: String,
  override val name: String,
  val kind: ResourceKind
) : Dependency(type, region, name)

enum class DependencyType {
  SECURITY_GROUP, LOAD_BALANCER, TARGET_GROUP, GENERIC_RESOURCE
}
