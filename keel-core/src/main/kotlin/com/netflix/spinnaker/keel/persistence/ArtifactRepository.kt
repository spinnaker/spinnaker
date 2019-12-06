package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.EnvironmentArtifactsSummary
import org.slf4j.LoggerFactory
import org.springframework.util.comparator.NullSafeComparator

interface ArtifactRepository {

  /**
   * Creates or updates a registered artifact
   */
  // todo eb: https://github.com/spinnaker/keel/issues/655
  fun register(artifact: DeliveryArtifact)

  fun get(name: String, type: ArtifactType): DeliveryArtifact

  fun isRegistered(name: String, type: ArtifactType): Boolean

  fun getAll(type: ArtifactType? = null): List<DeliveryArtifact>

  /**
   * @return `true` if a new version is persisted, `false` if the specified version was already
   * known (in which case this method is a no-op).
   */
  fun store(artifact: DeliveryArtifact, version: String, status: ArtifactStatus?): Boolean

  /**
   * @returns the versions we have for an artifact, optionally filtering by status if provided
   */
  fun versions(
    name: String,
    type: ArtifactType,
    statuses: List<ArtifactStatus> = emptyList()
  ): List<String>

  fun versions(
    artifact: DeliveryArtifact,
    statuses: List<ArtifactStatus> = enumValues<ArtifactStatus>().toList()
  ): List<String>

  /**
   * @return the latest version of [artifact] approved for use in [targetEnvironment],
   * optionally filtering by status if provided.
   */
  fun latestVersionApprovedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    targetEnvironment: String,
    statuses: List<ArtifactStatus> = emptyList()
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
}

class NoSuchArtifactException(name: String, type: ArtifactType) :
  RuntimeException("No $type artifact named $name is registered") {
  constructor(artifact: DeliveryArtifact) : this(artifact.name, artifact.type)
}

class ArtifactAlreadyRegistered(name: String, type: ArtifactType) :
  RuntimeException("The $type artifact $name is already registered") {
  constructor(artifact: DeliveryArtifact) : this(artifact.name, artifact.type)
}
