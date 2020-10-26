package com.netflix.spinnaker.keel.api.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DEFAULT_MAX_ARTIFACT_VERSIONS
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState

/**
 * A read-only repository for interacting with delivery configs, artifacts, and resources.
 */
interface KeelReadOnlyRepository {
  fun getDeliveryConfig(name: String): DeliveryConfig

  fun environmentFor(resourceId: String): Environment

  fun deliveryConfigFor(resourceId: String): DeliveryConfig

  fun getDeliveryConfigForApplication(application: String): DeliveryConfig

  fun getConstraintState(deliveryConfigName: String, environmentName: String, artifactVersion: String, type: String, artifactReference: String?): ConstraintState?

  fun constraintStateFor(application: String): List<ConstraintState>

  fun constraintStateFor(deliveryConfigName: String, environmentName: String, limit: Int): List<ConstraintState>

  fun constraintStateFor(deliveryConfigName: String, environmentName: String, artifactVersion: String): List<ConstraintState>

  fun pendingConstraintVersionsFor(deliveryConfigName: String, environmentName: String): List<String>

  fun getQueuedConstraintApprovals(deliveryConfigName: String, environmentName: String, artifactReference: String?): Set<String>

  fun getResource(id: String): Resource<ResourceSpec>

  fun getRawResource(id: String): Resource<ResourceSpec>

  fun hasManagedResources(application: String): Boolean

  fun getResourceIdsByApplication(application: String): List<String>

  fun getResourcesByApplication(application: String): List<Resource<*>>

  fun getArtifact(name: String, type: ArtifactType, deliveryConfigName: String): List<DeliveryArtifact>

  fun getArtifact(name: String, type: ArtifactType, reference: String, deliveryConfigName: String): DeliveryArtifact

  fun getArtifact(deliveryConfigName: String, reference: String): DeliveryArtifact

  fun isRegistered(name: String, type: ArtifactType): Boolean

  fun artifactVersions(artifact: DeliveryArtifact, limit: Int = DEFAULT_MAX_ARTIFACT_VERSIONS): List<String>

  fun artifactVersions(name: String, type: ArtifactType): List<String>

  fun latestVersionApprovedIn(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, targetEnvironment: String): String?

  fun isApprovedFor(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean

  fun wasSuccessfullyDeployedTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean

  fun isCurrentlyDeployedTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean

  /**
   * Returns the release status for the specified [version] of the [artifact], if available.
   */
  fun getReleaseStatus(artifact: DeliveryArtifact, version: String): ArtifactStatus?
}
