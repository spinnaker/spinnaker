package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact

data class DeliveryConfig(
  val name: String,
  val application: String,
  val serviceAccount: String,
  val artifacts: Set<DeliveryArtifact> = emptySet(),
  val environments: Set<Environment> = emptySet(),
  val apiVersion: String = "delivery.config.spinnaker.netflix.com/v1"
) {
  val resources: Set<Resource<*>>
    get() = environments.flatMapTo(mutableSetOf()) { it.resources }

  fun matchingArtifactByReference(reference: String): DeliveryArtifact? =
    artifacts.find { it.reference == reference }

  fun matchingArtifactByName(name: String, type: ArtifactType): DeliveryArtifact? =
    artifacts.find { it.name == name && it.type == type }
}
