package com.netflix.spinnaker.keel.plugin

/**
 * Represents the desired and current state of a resource. These two values are used as the basis of
 * a diff so if desired and current state are in alignment they MUST compare equal.
 *
 * @property desired the desired state.
 * @property current the current actual state. A `null` value means the resource does not exist.
 */
data class ResolvedResource<T>(
  val desired: T,
  val current: T?
)
