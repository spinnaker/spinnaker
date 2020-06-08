package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.core.api.ApplicationSummary
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVetoes
import com.netflix.spinnaker.keel.core.api.EnvironmentSummary
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.core.api.UID
import com.netflix.spinnaker.keel.core.api.normalize
import com.netflix.spinnaker.keel.events.ApplicationEvent
import com.netflix.spinnaker.keel.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceHistoryEvent
import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRED
import org.springframework.transaction.annotation.Transactional

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
  override val clock: Clock,
  override val publisher: ApplicationEventPublisher
) : KeelRepository {

  override val log by lazy { LoggerFactory.getLogger(javaClass) }

  @Transactional(propagation = REQUIRED)
  override fun upsertDeliveryConfig(submittedDeliveryConfig: SubmittedDeliveryConfig): DeliveryConfig {
    val new = DeliveryConfig(
      name = submittedDeliveryConfig.safeName,
      application = submittedDeliveryConfig.application,
      serviceAccount = submittedDeliveryConfig.serviceAccount ?: error("No service account specified, and no default applied"),
      artifacts = submittedDeliveryConfig.artifacts.transform(submittedDeliveryConfig.safeName),
      environments = submittedDeliveryConfig.environments.mapTo(mutableSetOf()) { env ->
        Environment(
          name = env.name,
          resources = env.resources.mapTo(mutableSetOf()) { resource ->
            resource
              .copy(metadata = mapOf("serviceAccount" to submittedDeliveryConfig.serviceAccount) + resource.metadata)
              .normalize()
          },
          constraints = env.constraints,
          notifications = env.notifications
        )
      },
      metadata = submittedDeliveryConfig.metadata ?: emptyMap()
    )
    return upsertDeliveryConfig(new)
  }

  @Transactional(propagation = REQUIRED)
  override fun upsertDeliveryConfig(deliveryConfig: DeliveryConfig): DeliveryConfig {
    val old = try {
      getDeliveryConfig(deliveryConfig.name)
    } catch (e: NoSuchDeliveryConfigException) {
      null
    }

    val existingConfig = try {
      getDeliveryConfigForApplication(deliveryConfig.application)
    } catch (e: NoSuchDeliveryConfigException) {
      null
    }

    if (old == null && existingConfig != null) {
      // we only allow one delivery config, so throw an error if someone is trying to submit a new config
      // instead of updating the existing config
      throw TooManyDeliveryConfigsException(deliveryConfig.application, existingConfig.name)
    }

    deliveryConfig.resources.forEach { resource ->

      upsertResource(resource, deliveryConfig.name)
    }

    deliveryConfig.artifacts.forEach { artifact ->
      register(artifact)
    }

    storeDeliveryConfig(deliveryConfig)

    if (old != null) {
      removeDependents(old, deliveryConfig)
    }
    return deliveryConfig
  }

  /**
   * Fetches delivery config by name, then deletes everything in it.
   */
  @Transactional(propagation = REQUIRED)
  override fun deleteDeliveryConfig(deliveryConfigName: String): DeliveryConfig =
    deleteDeliveryConfigFull(deliveryConfigRepository.get(deliveryConfigName))

  /**
   * Fetches delivery config by application, then deletes everything in it.
   */
  @Transactional(propagation = REQUIRED)
  override fun deleteDeliveryConfigByApplication(application: String): DeliveryConfig =
    deleteDeliveryConfigFull(deliveryConfigRepository.getByApplication(application))

  /**
   * Deletes a delivery config and everything in it.
   */
  @Transactional(propagation = REQUIRED)
  internal fun deleteDeliveryConfigFull(deliveryConfig: DeliveryConfig): DeliveryConfig {
    deliveryConfig.environments.forEach { environment ->
      environment.resources.forEach { resource ->
        // resources must be removed from the environment then deleted
        deliveryConfigRepository.deleteResourceFromEnv(deliveryConfig.name, environment.name, resource.id)
        deleteResource(resource.id)
      }
      deliveryConfigRepository.deleteEnvironment(deliveryConfig.name, environment.name)
    }

    deliveryConfig.artifacts.forEach { artifact ->
      artifactRepository.delete(artifact)
    }

    deliveryConfigRepository.delete(deliveryConfig.name)

    return deliveryConfig
  }

  /**
   * Removes artifacts, environments, and resources that were present in the [old]
   * delivery config and are not present in the [new] delivery config
   */
  override fun removeDependents(old: DeliveryConfig, new: DeliveryConfig) {
    val newResources = new.resources.map { it.id }

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

    old.environments
      .forEach { environment ->
        environment.resources.forEach { resource ->
          if (resource.id !in newResources) {
            log.debug("Updating config ${new.name}: removing resource ${resource.id} in environment ${environment.name}")
            deliveryConfigRepository.deleteResourceFromEnv(
              deliveryConfigName = old.name, environmentName = environment.name, resourceId = resource.id
            )
            deleteResource(resource.id)
          }
        }
        if (environment.name !in new.environments.map { it.name }) {
          log.debug("Updating config ${new.name}: removing environment ${environment.name}")
          deliveryConfigRepository.deleteEnvironment(new.name, environment.name)
        }
      }
  }

  private fun Set<DeliveryArtifact>.transform(deliveryConfigName: String) =
    map { artifact ->
      when (artifact) {
        is DockerArtifact -> artifact.copy(deliveryConfigName = deliveryConfigName)
        is DebianArtifact -> artifact.copy(deliveryConfigName = deliveryConfigName)
      }
    }.toSet()

  // START Delivery config methods
  override fun storeDeliveryConfig(deliveryConfig: DeliveryConfig) =
    deliveryConfigRepository.store(deliveryConfig)

  override fun getDeliveryConfig(name: String): DeliveryConfig =
    deliveryConfigRepository.get(name)

  override fun environmentFor(resourceId: String): Environment =
    deliveryConfigRepository.environmentFor(resourceId)

  override fun deliveryConfigFor(resourceId: String): DeliveryConfig =
    deliveryConfigRepository.deliveryConfigFor(resourceId)

  override fun getDeliveryConfigForApplication(application: String): DeliveryConfig =
    deliveryConfigRepository.getByApplication(application)

  override fun deleteResourceFromEnv(deliveryConfigName: String, environmentName: String, resourceId: String) =
    deliveryConfigRepository.deleteResourceFromEnv(deliveryConfigName, environmentName, resourceId)

  override fun deleteEnvironment(deliveryConfigName: String, environmentName: String) =
    deliveryConfigRepository.deleteEnvironment(deliveryConfigName, environmentName)

  override fun storeConstraintState(state: ConstraintState) =
    deliveryConfigRepository.storeConstraintState(state)

  override fun getConstraintState(deliveryConfigName: String, environmentName: String, artifactVersion: String, type: String): ConstraintState? =
    deliveryConfigRepository.getConstraintState(deliveryConfigName, environmentName, artifactVersion, type)

  override fun constraintStateFor(deliveryConfigName: String, environmentName: String, artifactVersion: String): List<ConstraintState> =
    deliveryConfigRepository.constraintStateFor(deliveryConfigName, environmentName, artifactVersion)

  override fun pendingConstraintVersionsFor(deliveryConfigName: String, environmentName: String): List<String> =
    deliveryConfigRepository.pendingConstraintVersionsFor(deliveryConfigName, environmentName)

  override fun getQueuedConstraintApprovals(deliveryConfigName: String, environmentName: String): Set<String> =
    deliveryConfigRepository.getQueuedConstraintApprovals(deliveryConfigName, environmentName)

  override fun queueAllConstraintsApproved(deliveryConfigName: String, environmentName: String, artifactVersion: String) =
    deliveryConfigRepository.queueAllConstraintsApproved(deliveryConfigName, environmentName, artifactVersion)

  override fun deleteQueuedConstraintApproval(deliveryConfigName: String, environmentName: String, artifactVersion: String) =
    deliveryConfigRepository.deleteQueuedConstraintApproval(deliveryConfigName, environmentName, artifactVersion)

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

  override fun getApplicationSummaries(): Collection<ApplicationSummary> =
    deliveryConfigRepository.getApplicationSummaries()
  // END DeliveryConfigRepository methods

  // START ResourceRepository methods
  override fun allResources(callback: (ResourceHeader) -> Unit) =
    resourceRepository.allResources(callback)

  override fun getResource(id: String): Resource<ResourceSpec> =
    resourceRepository.get(id)

  override fun hasManagedResources(application: String): Boolean =
    resourceRepository.hasManagedResources(application)

  override fun getResourceIdsByApplication(application: String): List<String> =
    resourceRepository.getResourceIdsByApplication(application)

  override fun getResourcesByApplication(application: String): List<Resource<*>> =
    resourceRepository.getResourcesByApplication(application)

  override fun storeResource(resource: Resource<*>) =
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

  override fun artifactsDueForCheck(minTimeSinceLastCheck: Duration, limit: Int): Collection<DeliveryArtifact> =
    artifactRepository.itemsDueForCheck(minTimeSinceLastCheck, limit)

  // END ResourceRepository methods

  // START ArtifactRepository methods
  override fun register(artifact: DeliveryArtifact) {
    artifactRepository.register(artifact)
    publisher.publishEvent(ArtifactRegisteredEvent(artifact))
  }

  override fun getArtifact(name: String, type: ArtifactType, deliveryConfigName: String): List<DeliveryArtifact> =
    artifactRepository.get(name, type, deliveryConfigName)

  override fun getArtifact(name: String, type: ArtifactType, reference: String, deliveryConfigName: String): DeliveryArtifact =
    artifactRepository.get(name, type, reference, deliveryConfigName)

  override fun getArtifact(deliveryConfigName: String, reference: String): DeliveryArtifact =
    artifactRepository.get(deliveryConfigName, reference)

  override fun isRegistered(name: String, type: ArtifactType): Boolean =
    artifactRepository.isRegistered(name, type)

  override fun getAllArtifacts(type: ArtifactType?): List<DeliveryArtifact> =
    artifactRepository.getAll(type)

  override fun storeArtifact(name: String, type: ArtifactType, version: String, status: ArtifactStatus?): Boolean =
    artifactRepository.store(name, type, version, status)

  override fun storeArtifact(artifact: DeliveryArtifact, version: String, status: ArtifactStatus?): Boolean =
    artifactRepository.store(artifact, version, status)

  override fun deleteArtifact(artifact: DeliveryArtifact) =
    artifactRepository.delete(artifact)

  override fun artifactVersions(artifact: DeliveryArtifact): List<String> =
    artifactRepository.versions(artifact)

  override fun artifactVersions(name: String, type: ArtifactType): List<String> =
    artifactRepository.versions(name, type)

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

  override fun markAsSuccessfullyDeployedTo(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String) =
    artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, version, targetEnvironment)

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

  override fun markAsSkipped(deliveryConfig: DeliveryConfig, artifact: DeliveryArtifact, version: String, targetEnvironment: String, supersededByVersion: String) {
    artifactRepository.markAsSkipped(deliveryConfig, artifact, version, targetEnvironment, supersededByVersion)
  }

  override fun getArtifactSummaryInEnvironment(
    deliveryConfig: DeliveryConfig,
    environmentName: String,
    artifactReference: String,
    version: String
  ) = artifactRepository.getArtifactSummaryInEnvironment(
    deliveryConfig, environmentName, artifactReference, version
  )

  // END ArtifactRepository methods
}
