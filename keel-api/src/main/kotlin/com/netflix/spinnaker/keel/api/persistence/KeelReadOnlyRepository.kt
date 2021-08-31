package com.netflix.spinnaker.keel.api.persistence

import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.action.Action
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.action.ActionStateFull
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter.ATTACH_ALL
import java.time.Duration

/**
 * A read-only repository for interacting with delivery configs, artifacts, and resources.
 */
interface KeelReadOnlyRepository {
  fun getDeliveryConfig(name: String): DeliveryConfig

  fun environmentFor(resourceId: String): Environment

  fun environmentNotifications(deliveryConfigName: String, environmentName: String): Set<NotificationConfig>

  fun deliveryConfigFor(resourceId: String): DeliveryConfig

  fun getDeliveryConfigForApplication(application: String): DeliveryConfig

  fun isApplicationConfigured(application: String): Boolean

  /**
   * Retrieves all available [DeliveryConfig] entries in the database.
   *
   * Because this is a potentially expensive set of queries, this method allows you to specify
   * which "dependents" (artifacts, environments and preview environments) you want to load with
   * the delivery config. The default is to load the complete delivery config with all dependents
   * attached, but you can specify one or more filters depending on the data you're interested in.
   *
   * @return The set of all available [DeliveryConfig] entries.
   */
  fun allDeliveryConfigs(vararg dependentAttachFilter: DependentAttachFilter = arrayOf(ATTACH_ALL)): Set<DeliveryConfig>

  fun getConstraintState(deliveryConfigName: String, environmentName: String, artifactVersion: String, type: String, artifactReference: String?): ConstraintState?

  fun constraintStateFor(application: String): List<ConstraintState>

  fun constraintStateFor(deliveryConfigName: String, environmentName: String, limit: Int): List<ConstraintState>

  fun constraintStateFor(deliveryConfigName: String, environmentName: String, artifactVersion: String, artifactReference: String): List<ConstraintState>

  fun constraintStateForEnvironments(deliveryConfigName: String, environmentUIDs: List<String> = emptyList()): List<ConstraintState>

  fun getPendingConstraintsForArtifactVersions(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact): List<PublishedArtifact>

  fun getArtifactVersionsQueuedForApproval(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact): List<PublishedArtifact>

  fun getResource(id: String): Resource<ResourceSpec>

  fun getRawResource(id: String): Resource<ResourceSpec>

  fun hasManagedResources(application: String): Boolean

  fun getResourceIdsByApplication(application: String): List<String>

  fun getResourcesByApplication(application: String): List<Resource<*>>

  fun getArtifact(name: String, type: ArtifactType, deliveryConfigName: String): List<DeliveryArtifact>

  fun getArtifact(name: String, type: ArtifactType, reference: String, deliveryConfigName: String): DeliveryArtifact

  fun getArtifact(deliveryConfigName: String, reference: String): DeliveryArtifact

  fun isRegistered(name: String, type: ArtifactType): Boolean

  fun artifactVersions(artifact: DeliveryArtifact, limit: Int): List<PublishedArtifact>

  fun getVersionsWithoutMetadata(limit: Int, maxAge: Duration): List<PublishedArtifact>

  fun getVerificationStatesBatch(contexts: List<ArtifactInEnvironmentContext>) : List<Map<String, ActionState>>

  fun getAllActionStatesBatch(contexts: List<ArtifactInEnvironmentContext>) : List<List<ActionStateFull>>

  fun getActionState(context: ArtifactInEnvironmentContext, action: Action): ActionState?

  fun latestVersionApprovedIn(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, targetEnvironment: String): String?

  fun isApprovedFor(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean

  fun wasSuccessfullyDeployedTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean

  fun isCurrentlyDeployedTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean

  fun getCurrentlyDeployedArtifactVersion(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    environmentName: String
  ): PublishedArtifact?

  /**
   * Returns the release status for the specified [version] of the [artifact], if available.
   */
  fun getReleaseStatus(artifact: DeliveryArtifact, version: String): ArtifactStatus?
}
