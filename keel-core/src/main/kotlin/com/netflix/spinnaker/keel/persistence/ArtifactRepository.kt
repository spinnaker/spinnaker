package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.api.EnvironmentArtifactsSummary
import com.netflix.spinnaker.keel.api.PinnedEnvironment

interface ArtifactRepository {

  /**
   * Creates or updates a registered artifact
   */
  fun register(artifact: DeliveryArtifact)

  fun get(name: String, type: ArtifactType, deliveryConfigName: String): List<DeliveryArtifact>

  fun get(name: String, type: ArtifactType, reference: String, deliveryConfigName: String): DeliveryArtifact

  fun get(deliveryConfigName: String, reference: String, type: ArtifactType): DeliveryArtifact

  fun isRegistered(name: String, type: ArtifactType): Boolean

  fun getAll(type: ArtifactType? = null): List<DeliveryArtifact>

  /**
   * @return `true` if a new version is persisted, `false` if the specified version was already
   * known (in which case this method is a no-op).
   */
  fun store(name: String, type: ArtifactType, version: String, status: ArtifactStatus?): Boolean

  fun store(artifact: DeliveryArtifact, version: String, status: ArtifactStatus?): Boolean

  /**
   * Deletes an artifact from a delivery config.
   * Does not remove the registration of an artifact.
   */
  fun delete(artifact: DeliveryArtifact)

  /**
   * @returns the versions we have for an artifact, filtering by the artifact status information,
   * and sorting with the artifact's sorting strategy
   */
  fun versions(
    artifact: DeliveryArtifact
  ): List<String>

  /**
   * Lists all versions, unsorted and regardless of status
   */
  fun versions(
    name: String,
    type: ArtifactType
  ): List<String>

  /**
   * @return the latest version of [artifact] approved for use in [targetEnvironment]
   *
   * Only versions that meet the status requirements for an artifact can be approved
   */
  fun latestVersionApprovedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    targetEnvironment: String
  ): String?

  /**
   * Marks [version] as approved for deployment to [targetEnvironment]. This means it has passed
   * any constraints on the environment and may be selected for use in the desired state of any
   * clusters in the environment.
   *
   * @return `true` if the approved version for the environment changed, `false` if this version was
   * _already_ approved.
   */
  fun approveVersionFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean

  /**
   * @return `true` if version is approved for the [targetEnvironment], `false` otherwise
   */
  fun isApprovedFor(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean

  /**
   * Marks [version] as currently deploying to [targetEnvironment].
   */
  fun markAsDeployingTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  )

  /**
   * @return `true` if [version] has (previously or currently) been deployed successfully to
   * [targetEnvironment].
   */
  fun wasSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  ): Boolean

  /**
   * Marks [version] as successfully deployed to [targetEnvironment] (i.e. future calls to
   * [wasSuccessfullyDeployedTo] will return `true` for that version.
   */
  fun markAsSuccessfullyDeployedTo(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  )

  /**
   * Fetches the status of artifact versions in the environments of [deliveryConfig].
   */
  fun versionsByEnvironment(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactsSummary>

  /**
   * Pin an environment to only deploy a specific DeliveryArtifact version
   */
  fun pinEnvironment(deliveryConfig: DeliveryConfig, environmentArtifactPin: EnvironmentArtifactPin)

  /**
   * @return list of [PinnedEnvironment]'s if any of the environments in
   * [deliveryConfig] have been pinned to a specific artifact version.
   */
  fun pinnedEnvironments(deliveryConfig: DeliveryConfig): List<PinnedEnvironment>

  /**
   * Removes all artifact pins from [targetEnvironment].
   */
  fun deletePin(deliveryConfig: DeliveryConfig, targetEnvironment: String)

  /**
   * Removes a specific pin from [targetEnvironment], by [reference] and [type].
   */
  fun deletePin(deliveryConfig: DeliveryConfig, targetEnvironment: String, reference: String, type: ArtifactType)
}

class NoSuchArtifactException(name: String, type: ArtifactType) :
  RuntimeException("No $type artifact named $name is registered") {
  constructor(artifact: DeliveryArtifact) : this(artifact.name, artifact.type)
}

class ArtifactReferenceNotFoundException(deliveryConfig: String, reference: String, type: ArtifactType) :
  RuntimeException("No $type artifact with reference $reference in delivery config $deliveryConfig is registered")

class ArtifactNotFoundException(name: String, type: ArtifactType, reference: String, deliveryConfig: String?) :
  RuntimeException("No $type artifact named $name with reference $reference in delivery config $deliveryConfig is registered") {
  constructor(artifact: DeliveryArtifact) : this(artifact.name, artifact.type, artifact.reference, artifact.deliveryConfigName)
}

class ArtifactAlreadyRegistered(name: String, type: ArtifactType) :
  RuntimeException("The $type artifact $name is already registered") {
  constructor(artifact: DeliveryArtifact) : this(artifact.name, artifact.type)
}
