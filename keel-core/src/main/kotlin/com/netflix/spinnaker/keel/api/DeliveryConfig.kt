package com.netflix.spinnaker.keel.api

data class DeliveryConfig(
  val name: String,
  val application: String,
  val artifacts: Set<DeliveryArtifact> = emptySet(),
  val environments: Set<Environment> = emptySet()
)

data class SubmittedDeliveryConfig(
  val name: String,
  val application: String,
  val artifacts: Set<DeliveryArtifact> = emptySet(),
  val environments: Set<SubmittedEnvironment> = emptySet()
)

data class SubmittedEnvironment(
  val name: String,
  val resources: Set<SubmittedResource<*>>
)

val DeliveryConfig.resources: Set<Resource<*>>
  get() = environments.flatMapTo(mutableSetOf()) { it.resources }
