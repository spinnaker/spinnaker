package com.netflix.spinnaker.keel.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.api.verification.VerificationState
import com.netflix.spinnaker.keel.core.api.ApplicationSummary
import com.netflix.spinnaker.keel.core.api.ArtifactSummaryInEnvironment
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.core.api.PublishedArtifactInEnvironment
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.UID
import com.netflix.spinnaker.keel.events.ApplicationEvent
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceHistoryEvent
import com.netflix.spinnaker.keel.persistence.DependentAttachFilter.ATTACH_ALL
import com.netflix.spinnaker.keel.services.StatusInfoForArtifactInEnvironment
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRED
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * A combined repository for delivery configs, artifacts, and resources.
 *
 * This paves the way for re-thinking how we interact with sql/storing of resources,
 * and updating our internal repository structure to allow storing delivery configs to more easily also
 * store artifacts and resources.
 *
 * This also gives us an easy place to emit telemetry and events around the usage of methods.
 *
 * TODO eb: refactor repository interaction so transactionality is easier.
 */
@Component
class CombinedRepository(
  val deliveryConfigRepository: DeliveryConfigRepository,
  val artifactRepository: ArtifactRepository,
  val resourceRepository: ResourceRepository,
  val verificationRepository: VerificationRepository,
  override val clock: Clock,
  override val publisher: ApplicationEventPublisher,
  val objectMapper: ObjectMapper
) : KeelRepository {

  override val log by lazy { LoggerFactory.getLogger(javaClass) }

  @Transactional(propagation = REQUIRED)
  override fun upsertDeliveryConfig(submittedDeliveryConfig: SubmittedDeliveryConfig): DeliveryConfig {
    val new = submittedDeliveryConfig.toDeliveryConfig()
    return upsertDeliveryConfig(new)
  }

  @Transactional(propagation = REQUIRED)
  override fun upsertDeliveryConfig(deliveryConfig: DeliveryConfig): DeliveryConfig {
    val configWithSameName = try {
      getDeliveryConfig(deliveryConfig.name)
    } catch (e: NoSuchDeliveryConfigException) {
      null
    }

    if (configWithSameName != null && configWithSameName.application != deliveryConfig.application) {
      // we don't allow storing 2 configs with the same name, for different applications
      throw ConflictingDeliveryConfigsException(configWithSameName.application)
    }

    val existingApplicationConfig = try {
      getDeliveryConfigForApplication(deliveryConfig.application)
    } catch (e: NoSuchDeliveryConfigException) {
      null
    }

    if (configWithSameName == null && existingApplicationConfig != null) {
      // we only allow one delivery config, so throw an error if someone is trying to submit a new config
      // instead of updating the existing config for the same application
      throw TooManyDeliveryConfigsException(deliveryConfig.application, existingApplicationConfig.name)
    }

    // by this point, configWithSameName is the old delivery config for this application
    deliveryConfig.resources.forEach { resource ->
      upsertResource(resource, deliveryConfig.name)
    }

    deliveryConfig.artifacts.forEach { artifact ->
      register(artifact)
    }

    storeDeliveryConfig(deliveryConfig)

    if (configWithSameName != null) {
      removeDependents(configWithSameName, deliveryConfig)
    }
    return getDeliveryConfig(deliveryConfig.name)
  }

  /**
   * Deletes a config and everything in it and about it
   */
  override fun deleteDeliveryConfigByApplication(application: String) =
    deliveryConfigRepository.deleteByApplication(application)

  /**
   * Deletes a config and everything in it and about it
   */
  override fun deleteDeliveryConfigByName(name: String) {
    deliveryConfigRepository.deleteByName(name)
  }

  /**
   * Removes artifacts, environments, and resources that were present in the [old]
   * delivery config and are not present in the [new] delivery config
   */
  override fun removeDependents(old: DeliveryConfig, new: DeliveryConfig) {
    old.artifacts.forEach { artifact ->
      val stillPresent = new.artifacts.any {
        it.name == artifact.name &&
          it.type == artifact.type &&
          it.reference == artifact.reference
      }
      if (!stillPresent) {
        log.debug("Updating config ${new.name}: removing artifact $artifact")
        artifactRepository.delete(artifact)
      }
    }

    val newResources = new.resources.map { it.id }

    old.environments
      .forEach { environment ->
        if (environment.name !in new.environments.map { it.name }) {
          log.debug("Updating config ${new.name}: removing environment ${environment.name}")
          environment.resources.map(Resource<*>::id).forEach {
            // only delete the resource if it's not somewhere else in the delivery config -- e.g.
            // it's been moved from one environment to another or the environment has been renamed
            if (it !in newResources) {
              resourceRepository.delete(it)
            }
          }
          deliveryConfigRepository.deleteEnvironment(new.name, environment.name)
        }
      }

    old.previewEnvironments
      .forEach { previewEnvSpec ->
        if (previewEnvSpec.baseEnvironment !in new.previewEnvironments.map { it.baseEnvironment }) {
          log.debug("Updating config ${new.name}: removing preview environment $previewEnvSpec")
          deliveryConfigRepository.deletePreviewEnvironment(new.name, previewEnvSpec.baseEnvironment)
        }
      }
  }

  // START Delivery config methods
  override fun storeDeliveryConfig(deliveryConfig: DeliveryConfig) =
    deliveryConfigRepository.store(deliveryConfig)

  override fun getDeliveryConfig(name: String): DeliveryConfig =
    deliveryConfigRepository.get(name)

  override fun environmentFor(resourceId: String): Environment =
    deliveryConfigRepository.environmentFor(resourceId)

  override fun environmentNotifications(deliveryConfigName: String, environmentName: String): Set<NotificationConfig> =
    deliveryConfigRepository.environmentNotifications(deliveryConfigName, environmentName)

  override fun deliveryConfigFor(resourceId: String): DeliveryConfig =
    deliveryConfigRepository.deliveryConfigFor(resourceId)

  override fun getDeliveryConfigForApplication(application: String): DeliveryConfig =
    deliveryConfigRepository.getByApplication(application)

  override fun allDeliveryConfigs(vararg dependentAttachFilter: DependentAttachFilter): Set<DeliveryConfig> =
    deliveryConfigRepository.all(*dependentAttachFilter)

  override fun deleteResourceFromEnv(deliveryConfigName: String, environmentName: String, resourceId: String) =
    deliveryConfigRepository.deleteResourceFromEnv(deliveryConfigName, environmentName, resourceId)

  override fun deleteEnvironment(deliveryConfigName: String, environmentName: String) =
    deliveryConfigRepository.deleteEnvironment(deliveryConfigName, environmentName)

  override fun storeEnvironment(deliveryConfigName: String, environment: Environment) =
    deliveryConfigRepository.storeEnvironment(deliveryConfigName, environment)

  override fun storeConstraintState(state: ConstraintState) =
    deliveryConfigRepository.storeConstraintState(state)

  override fun getConstraintState(deliveryConfigName: String, environmentName: String, artifactVersion: String, type: String, artifactReference: String?): ConstraintState? =
    deliveryConfigRepository.getConstraintState(deliveryConfigName, environmentName, artifactVersion, type, artifactReference)

  override fun constraintStateFor(deliveryConfigName: String, environmentName: String, artifactVersion: String, artifactReference: String): List<ConstraintState> =
    deliveryConfigRepository.constraintStateFor(deliveryConfigName, environmentName, artifactVersion, artifactReference)

  override fun getPendingArtifactVersions(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact): List<PublishedArtifact> =
    deliveryConfigRepository.getPendingArtifactVersions(deliveryConfigName, environmentName, artifact)

  override fun getArtifactVersionsQueuedForApproval(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact): List<PublishedArtifact> =
    deliveryConfigRepository.getArtifactVersionsQueuedForApproval(deliveryConfigName, environmentName, artifact)

  override fun queueArtifactVersionForApproval(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact, artifactVersion: String) =
    deliveryConfigRepository.queueArtifactVersionForApproval(deliveryConfigName, environmentName, artifact, artifactVersion)

  override fun deleteArtifactVersionQueuedForApproval(deliveryConfigName: String, environmentName: String, artifact: DeliveryArtifact, artifactVersion: String) =
    deliveryConfigRepository.deleteArtifactVersionQueuedForApproval(deliveryConfigName, environmentName, artifact, artifactVersion)

  override fun getConstraintStateById(uid: UID): ConstraintState? =
    deliveryConfigRepository.getConstraintStateById(uid)

  override fun deleteConstraintState(deliveryConfigName: String, environmentName: String, type: String) =
    deliveryConfigRepository.deleteConstraintState(deliveryConfigName, environmentName, type)

  override fun constraintStateFor(application: String): List<ConstraintState> =
    deliveryConfigRepository.constraintStateFor(application)

  override fun constraintStateFor(deliveryConfigName: String, environmentName: String, limit: Int): List<ConstraintState> =
    deliveryConfigRepository.constraintStateFor(deliveryConfigName, environmentName, limit)

  override fun deliveryConfigsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryConfig> =
    deliveryConfigRepository.itemsDueForCheck(minTimeSinceLastCheck, limit)

  override fun markDeliveryConfigCheckComplete(deliveryConfig: DeliveryConfig) {
    deliveryConfigRepository.markCheckComplete(deliveryConfig)
  }

  override fun getApplicationSummaries(): Collection<ApplicationSummary> =
    deliveryConfigRepository.getApplicationSummaries()

  override fun triggerDeliveryConfigRecheck(application: String) =
    deliveryConfigRepository.triggerRecheck(application)
  // END DeliveryConfigRepository methods

  // START ResourceRepository methods
  override fun allResources(callback: (ResourceHeader) -> Unit) =
    resourceRepository.allResources(callback)

  override fun getResource(id: String): Resource<ResourceSpec> =
    resourceRepository.get(id)

  override fun getRawResource(id: String): Resource<ResourceSpec> =
    resourceRepository.getRaw(id)

  override fun hasManagedResources(application: String): Boolean =
    resourceRepository.hasManagedResources(application)

  override fun getResourceIdsByApplication(application: String): List<String> =
    resourceRepository.getResourceIdsByApplication(application)

  override fun getResourcesByApplication(application: String): List<Resource<*>> =
    resourceRepository.getResourcesByApplication(application)

  override fun <T : ResourceSpec> storeResource(resource: Resource<T>): Resource<T> =
    resourceRepository.store(resource)

  override fun deleteResource(id: String) =
    resourceRepository.delete(id)

  override fun applicationEventHistory(application: String, limit: Int): List<ApplicationEvent> =
    resourceRepository.applicationEventHistory(application, limit)

  override fun applicationEventHistory(application: String, downTo: Instant): List<ApplicationEvent> =
    resourceRepository.applicationEventHistory(application, downTo)

  override fun resourceEventHistory(id: String, limit: Int): List<ResourceHistoryEvent> =
    resourceRepository.eventHistory(id, limit)

  override fun lastResourceHistoryEvent(id: String): ResourceHistoryEvent? =
    resourceRepository.lastEvent(id)

  override fun appendResourceHistory(event: ResourceEvent) =
    resourceRepository.appendHistory(event)

  override fun appendApplicationHistory(event: ApplicationEvent) =
    resourceRepository.appendHistory(event)

  override fun resourcesDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<Resource<ResourceSpec>> =
    resourceRepository.itemsDueForCheck(minTimeSinceLastCheck, limit)

  override fun triggerResourceRecheck(environmentName: String, application: String) =
    resourceRepository.triggerResourceRecheck(environmentName, application)

  // END ResourceRepository methods

  // START ArtifactRepository methods
  override fun register(artifact: DeliveryArtifact) {
    artifactRepository.register(artifact)
    publisher.publishEvent(ArtifactRegisteredEvent(artifact))
  }

  override fun artifactsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryArtifact> =
    artifactRepository.itemsDueForCheck(minTimeSinceLastCheck, limit)

  override fun getArtifact(name: String, type: ArtifactType, deliveryConfigName: String): List<DeliveryArtifact> =
    artifactRepository.get(name, type, deliveryConfigName)

  override fun getArtifact(name: String, type: ArtifactType, reference: String, deliveryConfigName: String): DeliveryArtifact =
    artifactRepository.get(name, type, reference, deliveryConfigName)

  override fun getArtifact(deliveryConfigName: String, reference: String): DeliveryArtifact =
    artifactRepository.get(deliveryConfigName, reference)

  override fun isRegistered(name: String, type: ArtifactType): Boolean =
    artifactRepository.isRegistered(name, type)

  override fun getAllArtifacts(type: ArtifactType?, name: String?): List<DeliveryArtifact> =
    artifactRepository.getAll(type, name)

  override fun storeArtifactVersion(artifactVersion: PublishedArtifact): Boolean =
    artifactRepository.storeArtifactVersion(artifactVersion)

  override fun getArtifactVersion(artifact: DeliveryArtifact, version: String, status: ArtifactStatus?): PublishedArtifact? =
    artifactRepository.getArtifactVersion(artifact, version, status)

  override fun updateArtifactMetadata(artifact: PublishedArtifact, artifactMetadata: ArtifactMetadata) =
    artifactRepository.updateArtifactMetadata(artifact, artifactMetadata)

  override fun deleteArtifact(artifact: DeliveryArtifact) =
    artifactRepository.delete(artifact)

  override fun artifactVersions(artifact: DeliveryArtifact, limit: Int): List<PublishedArtifact> =
    artifactRepository.versions(artifact, limit)

  override fun latestVersionApprovedIn(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, targetEnvironment: String): String? =
    artifactRepository.latestVersionApprovedIn(deliveryConfig, artifact, targetEnvironment)

  override fun approveVersionFor(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean =
    artifactRepository.approveVersionFor(deliveryConfig, artifact, version, targetEnvironment)

  override fun isApprovedFor(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean =
    artifactRepository.isApprovedFor(deliveryConfig, artifact, version, targetEnvironment)

  override fun markAsDeployingTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String) =
    artifactRepository.markAsDeployingTo(deliveryConfig, artifact, version, targetEnvironment)

  override fun wasSuccessfullyDeployedTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean =
    artifactRepository.wasSuccessfullyDeployedTo(deliveryConfig, artifact, version, targetEnvironment)

  override fun isCurrentlyDeployedTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String): Boolean =
    artifactRepository.isCurrentlyDeployedTo(deliveryConfig, artifact, version, targetEnvironment)

  override fun getReleaseStatus(artifact: DeliveryArtifact, version: String): ArtifactStatus? =
    artifactRepository.getReleaseStatus(artifact, version)

  override fun markAsSuccessfullyDeployedTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String) =
    artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, version, targetEnvironment)

  override fun getArtifactVersionsByStatus(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    statuses: List<PromotionStatus>
  ): List<PublishedArtifact> =
    artifactRepository.getArtifactVersionsByStatus(deliveryConfig, environmentName, statuses)

  override fun getPendingVersionsInEnvironment(
    deliveryConfig: DeliveryConfig,
    artifactReference: String,
    environmentName: String
  ): List<PublishedArtifact> =
    artifactRepository.getPendingVersionsInEnvironment(deliveryConfig, artifactReference, environmentName)

  override fun getAllVersionsForEnvironment(
    artifact: DeliveryArtifact, config: DeliveryConfig, environmentName: String
  ): List<PublishedArtifactInEnvironment> =
    artifactRepository.getAllVersionsForEnvironment(artifact, config, environmentName)

  override fun getEnvironmentSummaries(deliveryConfig: DeliveryConfig): List<EnvironmentSummary> =
    artifactRepository.getEnvironmentSummaries(deliveryConfig)

  override fun pinEnvironment(deliveryConfig: DeliveryConfig, environmentArtifactPin: EnvironmentArtifactPin) =
    artifactRepository.pinEnvironment(deliveryConfig, environmentArtifactPin)

  override fun pinnedEnvironments(deliveryConfig: DeliveryConfig): List<PinnedEnvironment> =
    artifactRepository.getPinnedEnvironments(deliveryConfig)

  override fun deletePin(deliveryConfig: DeliveryConfig, targetEnvironment: String, reference: String?) =
    if (reference != null) {
      artifactRepository.deletePin(deliveryConfig, targetEnvironment, reference)
    } else {
      artifactRepository.deletePin(deliveryConfig, targetEnvironment)
    }

  override fun vetoedEnvironmentVersions(deliveryConfig: DeliveryConfig): List<EnvironmentArtifactVetoes> =
    artifactRepository.vetoedEnvironmentVersions(deliveryConfig)

  override fun markAsVetoedIn(
    deliveryConfig: DeliveryConfig,
    veto: EnvironmentArtifactVeto,
    force: Boolean
  ): Boolean =
    artifactRepository.markAsVetoedIn(deliveryConfig, veto, force)

  override fun deleteVeto(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String) =
    artifactRepository.deleteVeto(deliveryConfig, artifact, version, targetEnvironment)

  override fun markAsSkipped(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String, supersededByVersion: String?) {
    artifactRepository.markAsSkipped(deliveryConfig, artifact, version, targetEnvironment, supersededByVersion)
  }

  override fun getArtifactSummariesInEnvironment(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    artifactReference: String,
    versions: List<String>
  ): List<ArtifactSummaryInEnvironment> =
    artifactRepository.getArtifactSummariesInEnvironment(
      deliveryConfig, environmentName, artifactReference, versions
    )

  override fun getArtifactSummaryInEnvironment(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    artifactReference: String,
    version: String
  ) = artifactRepository.getArtifactSummaryInEnvironment(
    deliveryConfig, environmentName, artifactReference, version
  )

  override fun getArtifactVersionByPromotionStatus(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    artifact: DeliveryArtifact,
    promotionStatus: PromotionStatus,
    version: String?
  ) = artifactRepository.getArtifactVersionByPromotionStatus(
    deliveryConfig, environmentName, artifact, promotionStatus, version
  )

  override fun getVersionInfoInEnvironment(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    artifact: DeliveryArtifact
  ): List<StatusInfoForArtifactInEnvironment> =
    artifactRepository.getVersionInfoInEnvironment(deliveryConfig, environmentName, artifact)

  override fun getPinnedVersion(deliveryConfig: DeliveryConfig, targetEnvironment: String, reference: String)
    = artifactRepository.getPinnedVersion(deliveryConfig, targetEnvironment, reference)

  // END ArtifactRepository methods

  // START VerificationRepository methods
  override fun nextEnvironmentsForVerification(
    minTimeSinceLastCheck: Duration,
    limit: Int
  ) : Collection<VerificationContext> =
    verificationRepository.nextEnvironmentsForVerification(minTimeSinceLastCheck, limit)

  override fun updateState(
    context: VerificationContext,
    verification: Verification,
    status: ConstraintStatus,
    metadata: Map<String, Any?>,
    link: String?
  ) = verificationRepository.updateState(context, verification, status, metadata, link)


  override fun getVerificationStatesBatch(contexts: List<VerificationContext>) : List<Map<String, VerificationState>> =
    verificationRepository.getStatesBatch(contexts)

  // END VerificationRepository methods
}
