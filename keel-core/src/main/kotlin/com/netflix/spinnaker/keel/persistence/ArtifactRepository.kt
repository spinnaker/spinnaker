package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.ArtifactVersion
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.kork.exceptions.UserException
import java.time.Duration

interface ArtifactRepository : PeriodicallyCheckedRepository<DeliveryArtifact> {

  /**
   * Creates or updates a registered artifact
   */
  fun register(artifact: DeliveryArtifact)

  fun get(name: String, type: ArtifactType, deliveryConfigName: String): List<DeliveryArtifact>

  fun get(name: String, type: ArtifactType, reference: String, deliveryConfigName: String): DeliveryArtifact

  fun get(deliveryConfigName: String, reference: String): DeliveryArtifact

  fun isRegistered(name: String, type: ArtifactType): Boolean

  fun getAll(type: ArtifactType? = null): List<DeliveryArtifact>

  /**
   * @return `true` if a new version is persisted, `false` if the specified version was already
   * known (in which case this method is a no-op).
   */
  fun storeArtifactVersion(artifact: ArtifactVersion): Boolean

  /**
   * @return The given artifact version as a [ArtifactVersion]
   */
  fun getArtifactVersion(name: String, type: ArtifactType, version: String, status: ArtifactStatus?): ArtifactVersion?

  /**
   * Deletes an artifact from a delivery config.
   * Does not remove the registration of an artifact.
   */
  fun delete(artifact: DeliveryArtifact)

  /**
   * @returns the versions we have for an artifact, filtering by the artifact status information,
   * and sorting with the artifact's sorting strategy
   *
   * This endpoint filters out invalid docker tags (tags that produce 0 or 2+ capture groups according to
   * the supplied versioning strategy).
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
   * Returns the release status for the specified [version] of the [artifact], if available.
   */
  fun getReleaseStatus(artifact: DeliveryArtifact, version: String): ArtifactStatus?

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
   * @return `true` if [version] is currently deployed successfully to [targetEnvironment].
   */
  fun isCurrentlyDeployedTo(
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
   * @return list of [EnvironmentArtifactVetoes] for all environments and artifacts defined
   * in the [deliveryConfig].
   */
  fun vetoedEnvironmentVersions(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactVetoes>

  /**
   * Marks [version] as vetoed from consideration for deployment to [targetEnvironment].
   * When run against the latest approved version, the effect is a rollback to the last
   * previously deployed version. This is in contrast to [pinEnvironment], which forces
   * resolution of the desired artifact version to what is pinned. Only a single
   * (artifact, artifact_version) can be pinned per environment but many can be vetoed.
   *
   * @param force when set, the version will be vetoed even if it became the desired
   *  version due to a prior, potentially automated, veto.
   *
   * @return `true` if the veto was successfully persisted.
   */
  fun markAsVetoedIn(
    deliveryConfig: DeliveryConfig,
    veto: EnvironmentArtifactVeto,
    force: Boolean = false
  ): Boolean

  /**
   * Removes any veto of [version] in [targetEnvironment]]
   */
  fun deleteVeto(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String
  )

  /**
   * Marks a version of an artifact as skipped for an environment, with information on what version superseded it.
   */
  fun markAsSkipped(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String,
    supersededByVersion: String
  )

  /**
   * Fetches the status of artifact versions in the environments of [deliveryConfig].
   */
  fun getEnvironmentSummaries(deliveryConfig: DeliveryConfig): List<EnvironmentSummary>

  /**
   * Pin an environment to only deploy a specific DeliveryArtifact version
   */
  fun pinEnvironment(deliveryConfig: DeliveryConfig, environmentArtifactPin: EnvironmentArtifactPin)

  /**
   * @return list of [PinnedEnvironment]'s if any of the environments in
   * [deliveryConfig] have been pinned to a specific artifact version.
   */
  fun getPinnedEnvironments(deliveryConfig: DeliveryConfig): List<PinnedEnvironment>

  /**
   * Removes all artifact pins from [targetEnvironment].
   */
  fun deletePin(deliveryConfig: DeliveryConfig, targetEnvironment: String)

  /**
   * Removes a specific pin from [targetEnvironment], by [reference].
   */
  fun deletePin(deliveryConfig: DeliveryConfig, targetEnvironment: String, reference: String)

  /**
   * Given information about a delivery config, environment, artifact and version, returns a summary that can be
   * used by the UI.
   */
  fun getArtifactSummaryInEnvironment(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    artifactReference: String,
    version: String
  ): ArtifactSummaryInEnvironment?

  /**
   * Returns between zero and [limit] artifacts that have not been checked (i.e. returned by this
   * method) in at least [minTimeSinceLastCheck].
   *
   * This method is _not_ intended to be idempotent, subsequent calls are expected to return
   * different values.
   */
  override fun itemsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryArtifact>

  /**
   * Returns true if the version is older (lower) than the existing version.
   * Note: the artifact comparitors are decending by default
   */
  fun isOlder(artifact: DeliveryArtifact, new: String, existingVersion: String): Boolean =
    artifact.versioningStrategy.comparator.compare(new, existingVersion) > 0

  /**
   * Returns true if the version is newer (higher) than the existing version.
   * Note: the artifact comparitors are decending by default
   */
  fun isNewer(artifact: DeliveryArtifact, version: String, existingVersion: String): Boolean =
    artifact.versioningStrategy.comparator.compare(version, existingVersion) < 0

  /**
   * Given a list of pending versions and a current version, removes all versions older than the current version
   * from the list. If there's no current, returns all pending versions.
   */
  fun removeOlderIfCurrentExists(artifact: DeliveryArtifact, currentVersion: String?, pending: List<String>?): List<String> {
    if (pending == null) {
      return emptyList()
    }

    if (currentVersion == null) {
      return pending
    }

    return pending.filter { isNewer(artifact, it, currentVersion) }
  }

  /**
   * Given a list of pending versions and a current version, returns all versions older than the current
   * version from the list. If there's no current version or no pending versions, returns an empty list.
   */
  fun removeNewerIfCurrentExists(artifact: DeliveryArtifact, currentVersion: String?, pending: List<String>?): List<String> {
    if (pending == null || currentVersion == null) {
      return emptyList()
    }

    return pending.filter { isOlder(artifact, it, currentVersion) }
  }
}

class NoSuchArtifactException(name: String, type: ArtifactType) :
  NoSuchEntityException("No $type artifact named $name is registered") {
  constructor(artifact: DeliveryArtifact) : this(artifact.name, artifact.type)
}

class ArtifactNotFoundException(reference: String, deliveryConfig: String?) :
  NoSuchEntityException("No artifact with reference $reference in delivery config $deliveryConfig is registered")

class ArtifactAlreadyRegistered(name: String, type: ArtifactType) :
  UserException("The $type artifact $name is already registered")
