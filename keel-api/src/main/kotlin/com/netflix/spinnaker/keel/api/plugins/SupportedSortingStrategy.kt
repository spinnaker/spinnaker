package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.artifacts.SortingStrategy

/**
 * Tuple of [SortingStrategy] type name (e.g. "deb", "docker", etc.) and the
 * corresponding [SortingStrategy] sub-class, used to facilitate registration
 * and discovery of supported sorting strategies from [ArtifactSupplier] plugins.
 */
data class SupportedSortingStrategy<T : SortingStrategy>(
  val name: String,
  val strategyClass: Class<T>
)
