package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.persistence.NoMatchingArtifactException

const val DEFAULT_SERVICE_ACCOUNT = "keel@spinnaker.io"

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
  val resources: Set<SubmittedResource<*>>,
  val constraints: Set<Constraint> = emptySet(),
  val notifications: Set<NotificationConfig> = emptySet()
)

val DeliveryConfig.resources: Set<Resource<*>>
  get() = environments.flatMapTo(mutableSetOf()) { it.resources }

fun DeliveryConfig.matchingArtifact(reference: String, type: ArtifactType): DeliveryArtifact =
  artifacts.find { it.reference == reference && it.type == type }
    ?: throw NoMatchingArtifactException(name, type, reference)
