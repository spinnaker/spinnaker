package com.netflix.spinnaker.keel.api

data class DeliveryArtifact(
  val name: String,
  val type: ArtifactType = ArtifactType.DEB
)

enum class ArtifactType {
  DEB
}

// todo eb: add RELEASE
enum class ArtifactStatus {
  FINAL, CANDIDATE, SNAPSHOT
}
