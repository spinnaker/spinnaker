package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.constraints.ConstraintState
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactsSummary
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.UID
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceUpdated
import java.time.Clock
import java.time.Duration
import org.slf4j.Logger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * A repository for interacting with delivery configs, artifacts, and resources.
 */
interface KeelRepository {
  val clock: Clock
  val publisher: ApplicationEventPublisher
  val log: Logger

  @Transactional(propagation = Propagation.REQUIRED)
  fun upsertDeliveryConfig(submittedDeliveryConfig: SubmittedDeliveryConfig): DeliveryConfig

  @Transactional(propagation = Propagation.REQUIRED)
  fun upsertDeliveryConfig(deliveryConfig: DeliveryConfig): DeliveryConfig

  fun <T : ResourceSpec> upsert(resource: Resource<T>) {
    val existingResource = try {
      getResource(resource.id)
    } catch (e: NoSuchResourceException) {
      null
    }
    if (existingResource != null) {
      val diff = DefaultResourceDiff(resource.spec, existingResource.spec)
      if (diff.hasChanges()) {
        log.debug("Updating ${resource.id}")
        storeResource(resource)
        publisher.publishEvent(ResourceUpdated(resource, diff.toDeltaJson(), clock))
      }
    } else {
      log.debug("Creating $resource")
      storeResource(resource)
      publisher.publishEvent(ResourceCreated(resource, clock))
    }
  }

  /**
   * Deletes a delivery config and everything in it.
   */
  @Transactional(propagation = Propagation.REQUIRED)
  fun deleteDeliveryConfig(deliveryConfigName: String): DeliveryConfig

  /**
   * Removes artifacts, environments, and resources that were present in the [old]
   * delivery config and are not present in the [new] delivery config
   */
  fun removeResources(old: DeliveryConfig, new: DeliveryConfig)

  // START Delivery config methods
  fun storeDeliveryConfig(deliveryConfig: DeliveryConfig)

  fun getDeliveryConfig(name: String): DeliveryConfig

  fun environmentFor(resourceId: String): Environment

  fun deliveryConfigFor(resourceId: String): DeliveryConfig

  fun getDeliveryConfigsByApplication(application: String): Collection<DeliveryConfig>

  fun deleteDeliveryConfigByApplication(application: String): Int

  fun deleteResourceFromEnv(deliveryConfigName: String, environmentName: String, resourceId: String)

  fun deleteEnvironment(deliveryConfigName: String, environmentName: String)

  fun storeConstraintState(state: ConstraintState)

  fun getConstraintState(deliveryConfigName: String, environmentName: String, artifactVersion: String, type: String): ConstraintState?

  fun getConstraintStateById(uid: UID): ConstraintState?

  fun deleteConstraintState(deliveryConfigName: String, environmentName: String, type: String)

  fun constraintStateFor(application: String): List<ConstraintState>

  fun constraintStateFor(deliveryConfigName: String, environmentName: String, limit: Int): List<ConstraintState>

  fun deliveryConfigsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryConfig>
  // END DeliveryConfigRepository methods

  // START ResourceRepository methods
  fun allResources(callback: (ResourceHeader) -> Unit)

  fun getResource(id: String): Resource<out ResourceSpec>

  fun hasManagedResources(application: String): Boolean

  fun getResourceIdsByApplication(application: String): List<String>

  fun getResourcesByApplication(application: String): List<Resource<*>>

  fun getSummaryByApplication(application: String): List<ResourceSummary>

  fun storeResource(resource: Resource<*>)

  fun deleteResource(id: String)

  fun deleteResourcesByApplication(application: String): Int

  fun resourceEventHistory(id: String, limit: Int): List<ResourceEvent>

  fun resourceLastEvent(id: String): ResourceEvent?

  fun resourceAppendHistory(event: ResourceEvent)

  fun resourcesDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<Resource<out ResourceSpec>>

  fun getResourceStatus(id: String): ResourceStatus
  // END ResourceRepository methods

  // START ArtifactRepository methods
  fun register(artifact: DeliveryArtifact)

  fun getArtifact(name: String, type: ArtifactType, deliveryConfigName: String): List<DeliveryArtifact>

  fun getArtifact(name: String, type: ArtifactType, reference: String, deliveryConfigName: String): DeliveryArtifact

  fun getArtifact(deliveryConfigName: String, reference: String, type: ArtifactType): DeliveryArtifact

  fun isRegistered(name: String, type: ArtifactType): Boolean

  fun getAllArtifacts(type: ArtifactType?): List<DeliveryArtifact>

  fun storeArtifact(name: String, type: ArtifactType, version: String, status: ArtifactStatus?): Boolean

  fun storeArtifact(artifact: DeliveryArtifact, version: String, status: ArtifactStatus?): Boolean

  fun deleteArtifact(artifact: DeliveryArtifact)

  fun artifactVersions(artifact: DeliveryArtifact): List<String>

  fun artifactVersions(name: String, type: ArtifactType): List<String>

  fun latestVersionApprovedIn(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, targetEnvironment: String): String?

  fun approveVersionFor(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean

  fun isApprovedFor(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean

  fun markAsDeployingTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String)

  fun wasSuccessfullyDeployedTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean

  fun markAsSuccessfullyDeployedTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String)

  fun versionsByEnvironment(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactsSummary>

  fun pinEnvironment(deliveryConfig: DeliveryConfig, environmentArtifactPin: EnvironmentArtifactPin)

  fun pinnedEnvironments(deliveryConfig: DeliveryConfig): List<PinnedEnvironment>

  fun deletePin(deliveryConfig: DeliveryConfig, targetEnvironment: String)

  fun deletePin(deliveryConfig: DeliveryConfig, targetEnvironment: String, reference: String, type: ArtifactType)

  fun vetoedEnvironmentVersions(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactVetoes>

  fun markAsVetoedIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String,
    force: Boolean = false
  ): Boolean

  fun deleteVeto(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String)
  // END ArtifactRepository methods
}
