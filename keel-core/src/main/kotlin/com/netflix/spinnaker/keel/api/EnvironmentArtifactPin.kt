package com.netflix.spinnaker.keel.api

data class EnvironmentArtifactPin(
  val deliveryConfigName: String,
  val targetEnvironment: String,
  val reference: String,
  val type: String,
  val version: String?,
  val pinnedBy: String?,
  val comment: String?
)

data class PinnedEnvironment(
  val deliveryConfigName: String,
  val targetEnvironment: String,
  val artifact: DeliveryArtifact,
  val version: String
)
