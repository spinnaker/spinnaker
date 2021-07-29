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
 * TODO: replace usage of [DependencyType] with [ResourceKind] for all resource types. This
 *  is currently precluded by modeling load balancer dependencies without a kind (ALB, CLB, etc.).
 */
data class Dependency(
  val type: DependencyType,
  val region: String,
  val name: String,
  val kind: ResourceKind? = null
) {

  fun renamed(newName: String): Dependency =
    copy(name = newName)

  /**
   * Makes a best guess as to whether this [Dependency] is for a resource of the same kind as [resourceKind],
   * based on naming conventions we use for kinds.
   *
   * TODO: remove if/when all dependencies are implemented as [GENERIC_RESOURCE].
   */
  fun matchesKind(resourceKind: ResourceKind): Boolean =
    when (type) {
      GENERIC_RESOURCE -> kind == resourceKind
      else -> resourceKind.kind.contains(type.name.replace('_', '-').toLowerCase())
    }
}

enum class DependencyType {
  SECURITY_GROUP, LOAD_BALANCER, TARGET_GROUP, GENERIC_RESOURCE
}
