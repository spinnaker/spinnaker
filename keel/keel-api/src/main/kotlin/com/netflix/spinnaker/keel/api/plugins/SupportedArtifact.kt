package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact

/**
 * Tuple of [DeliveryArtifact] type name (e.g. "deb", "docker", etc.) and the
 * corresponding [DeliveryArtifact] sub-class, used to facilitate registration
 * and discovery of supported artifact types from [ArtifactSupplier] plugins.
 */
data class SupportedArtifact<T : DeliveryArtifact>(
  val name: String,
  val artifactClass: Class<T>
)
