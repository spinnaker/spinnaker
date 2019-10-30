package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

data class DeliveryArtifact(
  val name: String,
  val type: ArtifactType = ArtifactType.DEB
)

enum class ArtifactType(@JsonValue val friendlyName: String) {
  DEB("deb"),
  DOCKER("docker");

  companion object {
    @JsonCreator @JvmStatic
    fun fromFriendlyName(friendlyName: String): ArtifactType? {
      return valueOf(friendlyName.toUpperCase())
    }
  }
}

// todo eb: add RELEASE
enum class ArtifactStatus {
  FINAL, CANDIDATE, SNAPSHOT
}
