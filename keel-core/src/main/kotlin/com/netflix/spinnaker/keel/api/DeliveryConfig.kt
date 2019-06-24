package com.netflix.spinnaker.keel.api

data class DeliveryConfig(
  val name: String,
  val application: String,
  val artifacts: Set<DeliveryArtifact> = emptySet(),
  val environments: Set<Environment> = emptySet()
)
