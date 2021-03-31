package com.netflix.spinnaker.keel.core.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.schema.Description
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.artifacts.NpmArtifact
import com.netflix.spinnaker.keel.serialization.SubmittedEnvironmentDeserializer
import com.netflix.spinnaker.kork.exceptions.UserException

const val DEFAULT_SERVICE_ACCOUNT = "keel@spinnaker.io"

@Description("A manifest specifying the environments and resources that comprise an application.")
data class SubmittedDeliveryConfig(
  val application: String,
  val name: String?,
  @Description("The service account Spinnaker will authenticate with when making changes.")
  val serviceAccount: String?,
  val artifacts: Set<DeliveryArtifact> = emptySet(),
  val environments: Set<SubmittedEnvironment> = emptySet(),
  val metadata: Map<String, Any?>? = emptyMap()
) {
  val safeName: String
    @JsonIgnore get() = name ?: "$application-manifest"
  
  fun toDeliveryConfig(): DeliveryConfig = DeliveryConfig(
    name = safeName,
    application = application,
    serviceAccount = serviceAccount
      ?: error("No service account specified, and no default applied"),
    artifacts = artifacts.mapTo(mutableSetOf()) { artifact ->
      when (artifact) {
        is DebianArtifact -> artifact.copy(deliveryConfigName = safeName)
        is DockerArtifact -> artifact.copy(deliveryConfigName = safeName)
        is NpmArtifact -> artifact.copy(deliveryConfigName = safeName)
        else -> throw UserException("Unrecognized artifact sub-type: ${artifact.type} (${artifact.javaClass.name})")
      }
    },
    environments = environments.mapTo(mutableSetOf()) { env ->
      Environment(
        name = env.name,
        resources = env.resources.mapTo(mutableSetOf()) { resource ->
          resource
            .copy(metadata = mapOf("serviceAccount" to serviceAccount) + resource.metadata)
            .normalize()
        },
        constraints = env.constraints,
        verifyWith = env.verifyWith,
        notifications = env.notifications
      )
    },
    metadata = metadata ?: emptyMap()
  )
}

@JsonDeserialize(using = SubmittedEnvironmentDeserializer::class)
data class SubmittedEnvironment(
  val name: String,
  val resources: Set<SubmittedResource<*>>,
  val constraints: Set<Constraint> = emptySet(),
  val verifyWith: List<Verification> = emptyList(),
  val notifications: Set<NotificationConfig> = emptySet(),
  @Description("Optional locations that are propagated to any [resources] where they are not specified.")
  val locations: SubnetAwareLocations? = null
)
