package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact

data class DeliveryConfig(
  val application: String,
  val name: String,
  val serviceAccount: String,
  val artifacts: Set<DeliveryArtifact> = emptySet(),
  val environments: Set<Environment> = emptySet(),
  val previewEnvironments: Set<PreviewEnvironmentSpec> = emptySet(),
  val apiVersion: String = "delivery.config.spinnaker.netflix.com/v1",
  @get:ExcludedFromDiff
  val metadata: Map<String, Any?> = emptyMap(),
) {
  @get:ExcludedFromDiff
  val resources: Set<Resource<*>>
    get() = environments.flatMapTo(mutableSetOf()) { it.resources }

  fun matchingArtifactByReference(reference: String): DeliveryArtifact? =
    artifacts.find { it.reference == reference }

  fun matchingArtifactByName(name: String, type: ArtifactType): DeliveryArtifact? =
    artifacts.find { it.name == name && it.type == type }

  /**
   * Returns all artifacts used by resources in the environment
   */
  fun artifactsUsedIn(environmentName: String): Set<DeliveryArtifact> =
    environments
      .find { it.name == environmentName }
      ?.resources
        ?.mapNotNull { resource ->
          resource.findAssociatedArtifact(this)
        }
      ?.toSet() ?: emptySet()
}
