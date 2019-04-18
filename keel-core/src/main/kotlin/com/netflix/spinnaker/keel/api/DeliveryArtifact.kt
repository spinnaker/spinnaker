package com.netflix.spinnaker.keel.api

import java.net.URI

data class DeliveryArtifact(
  val name: String,
  val type: ArtifactType
)

enum class ArtifactType {
  DEB
}

data class DeliveryArtifactVersion(
  val artifact: DeliveryArtifact,
  val version: String,
  val provenance: URI
)
