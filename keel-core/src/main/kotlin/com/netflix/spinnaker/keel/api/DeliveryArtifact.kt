package com.netflix.spinnaker.keel.api

data class DeliveryArtifact(
  val name: String,
  val type: ArtifactType
)

enum class ArtifactType {
  DEB
}

enum class ArtifactStatus {
  RELEASE, FINAL, CANDIDATE, SNAPSHOT
}
