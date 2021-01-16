package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.core.api.ApplicationSummary
import com.netflix.spinnaker.keel.core.api.UID
import com.netflix.spinnaker.kork.exceptions.ConfigurationException
import com.netflix.spinnaker.kork.exceptions.SystemException
import java.time.Instant

interface DeliveryConfigRepository : PeriodicallyCheckedRepository<DeliveryConfig> {

  /**
   * Persists a [DeliveryConfig].
   */
  fun store(deliveryConfig: DeliveryConfig)

  /**
   * Retrieves a [DeliveryConfig] by its unique [name].
   *
   * @return The [DeliveryConfig]
   * @throws NoSuchDeliveryConfigException if [name] does not map to a persisted config
   */
  fun get(name: String): DeliveryConfig

  /**
   * Retrieve the [Environment] a resource belongs to, by the resource [id].
   */
  fun environmentFor(resourceId: String): Environment

  /**
   * Retrieve the [NotificationConfig] for an environment by environment name and delivery config name.
   */
  fun environmentNotifications(deliveryConfigName: String, environmentName: String): Set<NotificationConfig>

  /**
   * Retrieve the [DeliveryConfig] a resource belongs to (the parent of its environment).
   */
  fun deliveryConfigFor(resourceId: String): DeliveryConfig

  /**
   * @return the [DeliveryConfig] associated with [application], throws [NoDeliveryConfigForApplication] if none
   */
  fun getByApplication(application: String): DeliveryConfig

  /**
   * Deletes a delivery config and everything in it, based on the application name.
   */
  fun deleteByApplication(application: String)

  /**
   * Deletes a delivery config and everything in it.
   */
  fun deleteByName(name: String)

  /**
   * Removes a resource from an environment.
   */
  fun deleteResourceFromEnv(deliveryConfigName: String, environmentName: String, resourceId: String)

  /**
   * Deletes an environment from a delivery config.
   * Does not delete the resources within an environment
   */
  fun deleteEnvironment(deliveryConfigName: String, environmentName: String)

  /**
   * Updates state for a stateful [Environment] constraint.
   */
  fun storeConstraintState(state: ConstraintState)

  /**
   * Get the latest state of an [Environment] constraint for a specific artifact.
   *
   * @param deliveryConfigName the [DeliveryConfig] name
   * @param environmentName the [Environment] name
   * @param artifactVersion the version of the artifact we're checking constraint state for
   * @param type the type of constraint
   *
   * @return [ConstraintState] or `null` if the given constraint type has no state for
   * the given Artifact/Environment combination.
   */
  fun getConstraintState(
    deliveryConfigName: String,
    environmentName: String,
    artifactVersion: String,
    type: String,
    // FIXME: this should not be nullable as it's the only parameter uniquely identifying the artifact,
    //  but calls from the UI are currently not passing the artifact reference.
    artifactReference: String?
  ): ConstraintState?

  fun getConstraintStateById(
    uid: UID
  ): ConstraintState?

  /**
   * Removes constraint states from an [Environment] by [type].
   *
   * @param deliveryConfigName the [DeliveryConfig] name
   * @param environmentName the [Environment] name
   * @param type the type of the removed constraint
   */
  fun deleteConstraintState(
    deliveryConfigName: String,
    environmentName: String,
    type: String
  )

  /**
   * Rolls up the most recent constraint states (maximum of one per (Environment, ConstraintType))
   * related to a application retrieved by its name.
   *
   * @param application the application name
   *
   * @return A list of the most recent [ConstraintState]'s by environment per type or an
   * empty list if none exist.
   */
  fun constraintStateFor(application: String): List<ConstraintState>

  /**
   * Retrieves recent [ConstraintState]'s for an [Environment].
   *
   * @param deliveryConfigName the [DeliveryConfig] name
   * @param environmentName the [Environment] name
   * @param limit the maximum number of [ConstraintState]'s to return, sorted by recency
   *
   * @return A list of up-to the most recent `limit` [ConstraintState]'s or an empty list if
   * none exist.
   */
  fun constraintStateFor(
    deliveryConfigName: String,
    environmentName: String,
    limit: Int = 10
  ): List<ConstraintState>

  fun constraintStateFor(
    deliveryConfigName: String,
    environmentName: String,
    artifactVersion: String
  ): List<ConstraintState>

  /**
   * Fetches all versions have a pending stateful constraint in an environment
   */
  fun getPendingArtifactVersions(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact): List<PublishedArtifact>

  /**
   * Gets all versions queued for approval for the environment
   */
  fun getArtifactVersionsQueuedForApproval(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact): List<PublishedArtifact>

  /**
   * Adds an artifact version to the queued table to indicate all constraints pass for that version
   */
  fun queueArtifactVersionForApproval(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact, artifactVersion: String)

  /**
   * Removes a queued version from the queued table
   */
  fun deleteArtifactVersionQueuedForApproval(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact, artifactVersion: String)

  fun getApplicationSummaries(): Collection<ApplicationSummary>

  fun deliveryConfigLastChecked(deliveryConfig: DeliveryConfig): Instant
}

abstract class NoSuchDeliveryConfigException(message: String) :
  NoSuchEntityException(message)

class NoSuchDeliveryConfigName(name: String) :
  NoSuchDeliveryConfigException("No delivery config named $name exists in the database")

class NoDeliveryConfigForApplication(application: String) :
  NoSuchDeliveryConfigException("No delivery config for application $application exists in the database")

class NoMatchingArtifactException(deliveryConfigName: String, type: ArtifactType, reference: String) :
  NoSuchEntityException("No artifact with reference $reference and type $type found in delivery config $deliveryConfigName")

class TooManyDeliveryConfigsException(application: String, existing: String) :
  ConfigurationException("A delivery config already exists for application $application, and we only allow one per application - please delete existing config $existing before submitting a new config")

class ConflictingDeliveryConfigsException(application: String) :
  ConfigurationException("A delivery config already exists for a different application $application, and we don't allow delivery config name duplication - please select a different config name before submitting a new config")

class OrphanedResourceException(id: String) :
  SystemException("Resource $id exists without being a part of a delivery config")
