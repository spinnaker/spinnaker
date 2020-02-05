package com.netflix.spinnaker.keel.api

data class DeliveryConfig(
  val name: String,
  val application: String,
  val serviceAccount: String,
  val artifacts: Set<DeliveryArtifact> = emptySet(),
  val environments: Set<Environment> = emptySet(),
  val apiVersion: String = "delivery.config.spinnaker.netflix.com/v1"
)
