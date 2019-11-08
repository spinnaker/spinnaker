package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.annotation.JsonValue

data class DeliveryArtifact(
  val name: String,
  val type: ArtifactType = ArtifactType.DEB
)

enum class ArtifactType {
  DEB, DOCKER;

  @JsonValue
  fun value(): String = name.toLowerCase()
}

enum class ArtifactStatus {
  FINAL, CANDIDATE, SNAPSHOT, RELEASE
}
