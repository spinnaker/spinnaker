package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.netflix.spinnaker.keel.persistence.NoMatchingArtifactException
import com.netflix.spinnaker.keel.serialization.SubmittedEnvironmentDeserializer

const val DEFAULT_SERVICE_ACCOUNT = "keel@spinnaker.io"

data class DeliveryConfig(
  val name: String,
  val application: String,
  val serviceAccount: String,
  val artifacts: Set<DeliveryArtifact> = emptySet(),
  val environments: Set<Environment> = emptySet(),
  val apiVersion: String = "delivery.config.spinnaker.netflix.com/v1"
)

data class SubmittedDeliveryConfig(
  val name: String,
  val application: String,
  val serviceAccount: String,
  val artifacts: Set<DeliveryArtifact> = emptySet(),
  val environments: Set<SubmittedEnvironment> = emptySet()
)

@JsonDeserialize(using = SubmittedEnvironmentDeserializer::class)
data class SubmittedEnvironment(
  val name: String,
  val resources: Set<SubmittedResource<*>>,
  val constraints: Set<Constraint> = emptySet(),
  val notifications: Set<NotificationConfig> = emptySet(),
  /**
   * Optional locations that are propagated to any [resources] where they are not specified.
   */
  val locations: SubnetAwareLocations? = null
)

val DeliveryConfig.resources: Set<Resource<*>>
  get() = environments.flatMapTo(mutableSetOf()) { it.resources }

fun DeliveryConfig.matchingArtifact(reference: String, type: ArtifactType): DeliveryArtifact =
  artifacts.find { it.reference == reference && it.type == type }
    ?: throw NoMatchingArtifactException(name, type, reference)
