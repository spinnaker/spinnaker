package com.netflix.spinnaker.keel.api.artifacts

enum class ArtifactType {
  deb, docker;

  override fun toString() = name
}

enum class ArtifactStatus {
  FINAL, CANDIDATE, SNAPSHOT, RELEASE, UNKNOWN
}

abstract class DeliveryArtifact {
  abstract val name: String
  abstract val type: ArtifactType
  abstract val versioningStrategy: VersioningStrategy
  abstract val reference: String // friendly reference to use within a delivery config
  abstract val deliveryConfigName: String? // the delivery config this artifact is a part of
  override fun toString() = "$type:$name (ref: $reference)"
}
