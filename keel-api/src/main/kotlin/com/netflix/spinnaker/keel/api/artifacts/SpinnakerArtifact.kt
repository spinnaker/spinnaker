package com.netflix.spinnaker.keel.api.artifacts

/**
 * An immutable data class that mirrors com.netflix.spinnaker.kork.artifacts.model.Artifact, but without
 * all the Jackson baggage. One notable difference from the kork counterpart is that this class enforces
 * non-nullability of a few key fields without which it doesn't make sense for an artifact to exist in
 * Managed Delivery terms.
 */
data class SpinnakerArtifact(
  val name: String,
  val type: String,
  val reference: String,
  val version: String,
  val customKind: Boolean? = null,
  val location: String? = null,
  val artifactAccount: String? = null,
  val provenance: String? = null,
  val uuid: String? = null,
  val metadata: Map<String, Any?> = emptyMap()
)
