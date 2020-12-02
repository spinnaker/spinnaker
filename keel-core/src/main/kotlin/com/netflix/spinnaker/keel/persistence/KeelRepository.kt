package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.persistence.KeelReadOnlyRepository
import com.netflix.spinnaker.keel.core.api.ApplicationSummary
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.UID
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.events.ApplicationEvent
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceHistoryEvent
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.exceptions.DuplicateManagedResourceException
import com.netflix.spinnaker.keel.persistence.ResourceRepository.Companion.DEFAULT_MAX_EVENTS
import org.slf4j.Logger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * A repository for interacting with delivery configs, artifacts, and resources.
 */
interface KeelRepository : KeelReadOnlyRepository {
  val clock: Clock
  val publisher: ApplicationEventPublisher
  val log: Logger

  @Transactional(propagation = Propagation.REQUIRED)
  fun upsertDeliveryConfig(submittedDeliveryConfig: SubmittedDeliveryConfig): DeliveryConfig

  @Transactional(propagation = Propagation.REQUIRED)
  fun upsertDeliveryConfig(deliveryConfig: DeliveryConfig): DeliveryConfig

  fun <T : ResourceSpec> upsertResource(resource: Resource<T>, deliveryConfigName: String) {
    val existingResource = try {
      getRawResource(resource.id)
    } catch (e: NoSuchResourceException) {
      null
    }
    if (existingResource != null) {
      // we allow resources to be managed in a single delivery config file
      val existingConfig = deliveryConfigFor(existingResource.id)
      if (existingConfig.name != deliveryConfigName) {
        log.debug("resource $resource is being managed already by delivery config named ${existingConfig.name}")
        throw DuplicateManagedResourceException(resource.id, existingConfig.name, deliveryConfigName)
      }

      val diff = DefaultResourceDiff(resource.spec, existingResource.spec)
      if (diff.hasChanges() || resource.kind.version != existingResource.kind.version) {
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
  fun deleteDeliveryConfigByApplication(application: String)

  /**
   * Deletes a delivery config and everything in it.
   */
  fun deleteDeliveryConfigByName(name: String)

  /**
   * Removes artifacts, environments, and resources that were present in the [old]
   * delivery config and are not present in the [new] delivery config
   */
  fun removeDependents(old: DeliveryConfig, new: DeliveryConfig)

  // START Delivery config methods
  fun storeDeliveryConfig(deliveryConfig: DeliveryConfig)

  fun deleteResourceFromEnv(deliveryConfigName: String, environmentName: String, resourceId: String)

  fun deleteEnvironment(deliveryConfigName: String, environmentName: String)

  fun storeConstraintState(state: ConstraintState)

  fun getConstraintStateById(uid: UID): ConstraintState?

  fun deleteConstraintState(deliveryConfigName: String, environmentName: String, type: String)

  fun queueArtifactVersionForApproval(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact, artifactVersion: String)

  fun deleteArtifactVersionQueuedForApproval(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact, artifactVersion: String)

  fun deliveryConfigsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryConfig>

  fun markDeliveryConfigCheckComplete(deliveryConfig: DeliveryConfig)

  fun getApplicationSummaries(): Collection<ApplicationSummary>
  // END DeliveryConfigRepository methods

  // START ResourceRepository methods
  fun allResources(callback: (ResourceHeader) -> Unit)

  fun storeResource(resource: Resource<*>)

  fun deleteResource(id: String)

  fun applicationEventHistory(application: String, limit: Int = DEFAULT_MAX_EVENTS): List<ApplicationEvent>

  fun applicationEventHistory(application: String, downTo: Instant): List<ApplicationEvent>

  fun resourceEventHistory(id: String, limit: Int = DEFAULT_MAX_EVENTS): List<ResourceHistoryEvent>

  fun lastResourceHistoryEvent(id: String): ResourceHistoryEvent?

  fun appendResourceHistory(event: ResourceEvent)

  fun appendApplicationHistory(event: ApplicationEvent)

  fun resourcesDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<Resource<ResourceSpec>>

  fun artifactsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryArtifact>
  // END ResourceRepository methods

  // START ArtifactRepository methods
  fun register(artifact: DeliveryArtifact)

  fun getAllArtifacts(type: ArtifactType? = null, name: String? = null): List<DeliveryArtifact>

  fun storeArtifactVersion(artifactVersion: PublishedArtifact): Boolean

  fun getArtifactVersion(artifact: DeliveryArtifact, version: String, status: ArtifactStatus?): PublishedArtifact?

  fun updateArtifactMetadata(artifact: PublishedArtifact, artifactMetadata: ArtifactMetadata)

  fun deleteArtifact(artifact: DeliveryArtifact)

  fun approveVersionFor(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean

  fun markAsDeployingTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String)

  fun markAsSuccessfullyDeployedTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String)

  fun getEnvironmentSummaries(deliveryConfig: DeliveryConfig): List<EnvironmentSummary>

  fun pinEnvironment(deliveryConfig: DeliveryConfig, environmentArtifactPin: EnvironmentArtifactPin)

  fun pinnedEnvironments(deliveryConfig: DeliveryConfig): List<PinnedEnvironment>

  fun deletePin(deliveryConfig: DeliveryConfig, targetEnvironment: String, reference: String? = null)

  fun vetoedEnvironmentVersions(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactVetoes>

  fun markAsVetoedIn(
    deliveryConfig: DeliveryConfig,
    veto: EnvironmentArtifactVeto,
    force: Boolean = false
  ): Boolean

  fun deleteVeto(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String)

  fun markAsSkipped(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    targetEnvironment: String,
    supersededByVersion: String
  )

  /**
   * Given information about a delivery config, environment, artifact and version, returns a summary that can be
   * used by the UI, or null if the artifact version is not applicable to the environment.
   */
  fun getArtifactSummaryInEnvironment(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    artifactReference: String,
    version: String
  ): ArtifactSummaryInEnvironment?

  /**
   * Return the published artifact for the last deployed version that matches the promotion status
   */
  fun getArtifactVersionByPromotionStatus(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    artifact: DeliveryArtifact,
    promotionStatus: String,
    version: String? = null
  ): PublishedArtifact?

  /**
   * Return a specific artifact version if is pinned, from [targetEnvironment], by [reference], if exists.
   */
  fun getPinnedVersion(deliveryConfig: DeliveryConfig, targetEnvironment: String, reference: String): String?

  // END ArtifactRepository methods
}
