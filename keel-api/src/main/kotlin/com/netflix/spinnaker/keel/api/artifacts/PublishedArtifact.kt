package com.netflix.spinnaker.keel.api.artifacts

import java.time.Instant

/**
 * An immutable data class that represents a published software artifact in the Spinnaker ecosystem.
 *
 * This class mirrors [com.netflix.spinnaker.kork.artifacts.model.Artifact], but without all the Jackson baggage.
 * One notable difference from the kork counterpart is that this class enforces non-nullability of a few
 * key fields without which it doesn't make sense for an artifact to exist in Managed Delivery terms. It also adds
 * a couple of keel-specific fields to store artifact metadata.
 */
// TODO: rename to `Artifact` or `ArtifactInstance`
data class PublishedArtifact(
  val name: String,
  val type: String,
  val reference: String,
  val version: String,
  val customKind: Boolean? = null,
  val location: String? = null,
  val artifactAccount: String? = null,
  val provenance: String? = null,
  val uuid: String? = null,
  val metadata: Map<String, Any?> = emptyMap(),
  // keel-specific fields ---
  val gitMetadata: GitMetadata? = null,
  val buildMetadata: BuildMetadata? = null
) {

  // The stuff that matters to keel
  constructor(
    name: String,
    type: String,
    version: String,
    status: ArtifactStatus? = null,
    createdAt: Instant? = null,
    gitMetadata: GitMetadata? = null,
    buildMetadata: BuildMetadata? = null
  ) : this(
    name = name,
    type = type.toLowerCase(),
    reference = name,
    version = version,
    metadata = mapOf(
      "releaseStatus" to status?.name,
      "createdAt" to createdAt
    ),
    gitMetadata = gitMetadata,
    buildMetadata = buildMetadata
  )

  val status: ArtifactStatus? = metadata["releaseStatus"]?.toString()
    ?.let { ArtifactStatus.valueOf(it) }

  val createdAt = metadata["createdAt"]
    ?.let {
      when (it) {
        is Long -> Instant.ofEpochMilli(it) // to accommodate for artifact events from CI integration
        is Instant -> it
        else -> null
      }
    }

  // FIXME: it's silly that we're prepending the artifact name for Debian only...
  fun normalized() = copy(version = if (type == DEBIAN && !version.startsWith(name)) "$name-$version" else version)
}
