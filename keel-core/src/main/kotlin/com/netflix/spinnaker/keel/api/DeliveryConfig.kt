package com.netflix.spinnaker.keel.api

data class DeliveryConfig(
  val name: String,
  val application: String,
  val deliveryArtifacts: Set<DeliveryArtifact> = emptySet(),
  val deliveryEnvironments: Set<Environment> = emptySet()
)
