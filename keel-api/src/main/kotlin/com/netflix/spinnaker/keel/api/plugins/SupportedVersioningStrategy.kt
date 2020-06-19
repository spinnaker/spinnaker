package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.artifacts.VersioningStrategy

/**
 * Tuple of [VersioningStrategy] type name (e.g. "deb", "docker", etc.) and the
 * corresponding [VersioningStrategy] sub-class, used to facilitate registration
 * and discovery of supported versioning strategies from [ArtifactSupplier] plugins.
 */
data class SupportedVersioningStrategy<T : VersioningStrategy>(
  val name: String,
  val strategyClass: Class<T>
)
