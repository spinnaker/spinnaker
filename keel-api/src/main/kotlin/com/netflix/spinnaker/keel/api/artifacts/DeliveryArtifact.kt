package com.netflix.spinnaker.keel.api.artifacts

import com.netflix.spinnaker.keel.api.schema.Discriminator

typealias ArtifactType = String

const val DEBIAN: ArtifactType = "deb"
const val DOCKER: ArtifactType = "docker"
const val NPM: ArtifactType = "npm"

/**
 * The release status of an artifact. This may not necessarily be applicable to all
 * [DeliveryArtifact] sub-classes.
 */
enum class ArtifactStatus {
  FINAL, CANDIDATE, SNAPSHOT, RELEASE, UNKNOWN
}

/**
 * An artifact as defined in a [DeliveryConfig].
 *
 * Unlike other places within Spinnaker, this class does not describe a specific instance of a software artifact
 * (i.e. the output of a build that is published to an artifact repository), but rather the high-level properties
 * that allow keel and [ArtifactSupplier] plugins to find/process the actual artifacts.
 */
abstract class DeliveryArtifact {
  abstract val name: String
  @Discriminator abstract val type: ArtifactType
  abstract val versioningStrategy: VersioningStrategy
  abstract val reference: String // friendly reference to use within a delivery config
  abstract val deliveryConfigName: String? // the delivery config this artifact is a part of
  open val statuses: Set<ArtifactStatus> = emptySet()

  fun toPublishedArtifact(version: String, status: ArtifactStatus? = null) =
    PublishedArtifact(
      name = name,
      type = type,
      reference = reference,
      version = version,
      metadata = mapOf("releaseStatus" to status)
    ).normalized()

  override fun toString() = "${type.toUpperCase()} artifact $name (ref: $reference)"
}
