package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.events.ArtifactDeployedNotification
import com.netflix.spinnaker.keel.persistence.KeelRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Listens to [ArtifactVersionDeployed] events to update the status of the artifact in the database and
 * relay the event as an [ArtifactDeployedNotification] that can then be sent to end users.
 */
@Component
class ArtifactDeployedListener(
  private val repository: KeelRepository,
  val publisher: EventPublisher
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @EventListener(ArtifactVersionDeployed::class)
  fun onArtifactVersionDeployed(event: ArtifactVersionDeployed) =
    runBlocking {
      val resourceId = event.resourceId
      val resource = repository.getResource(resourceId)
      val deliveryConfig = repository.deliveryConfigFor(resourceId)
      val env = repository.environmentFor(resourceId)

      // if there's no artifact associated with this resource, we do nothing.
      val artifact = resource.findAssociatedArtifact(deliveryConfig) ?: return@runBlocking
        .also {
          log.debug("Unable to find artifact associated with resource $resourceId in application ${deliveryConfig.application}")
        }

      val approvedForEnv = repository.isApprovedFor(
        deliveryConfig = deliveryConfig,
        artifact = artifact,
        version = event.artifactVersion,
        targetEnvironment = env.name
      )

      if (approvedForEnv) {
        val markedCurrentlyDeployed = repository.getArtifactPromotionStatus(
          deliveryConfig = deliveryConfig,
          artifact = artifact,
          version = event.artifactVersion,
          targetEnvironment = env.name
        ) == CURRENT
        if (!markedCurrentlyDeployed) {
          log.info("Marking {} as deployed in {} for config {} because it's not currently marked as deployed", event.artifactVersion, env.name, deliveryConfig.name)
          repository.markAsSuccessfullyDeployedTo(
            deliveryConfig = deliveryConfig,
            artifact = artifact,
            version = event.artifactVersion,
            targetEnvironment = env.name
          )
          publisher.publishEvent(
            ArtifactDeployedNotification(
              config = deliveryConfig,
              deliveryArtifact = artifact,
              artifactVersion = event.artifactVersion,
              targetEnvironment = env
            )
          )
        } else {
          log.debug("$artifact version ${event.artifactVersion} is already marked as deployed to $env in" +
            " application ${deliveryConfig.application}")
        }
      } else {
        log.debug("$artifact version ${event.artifactVersion} is not approved for $env in application" +
          " ${deliveryConfig.application}, so not marking as deployed.")
      }
    }
}
