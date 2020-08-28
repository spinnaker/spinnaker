package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.persistence.KeelRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ArtifactDeployedListener(
  private val repository: KeelRepository
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

      val approvedForEnv = repository.isApprovedFor(
        deliveryConfig = deliveryConfig,
        artifact = artifact,
        version = event.artifactVersion,
        targetEnvironment = env.name
      )

      if (approvedForEnv) {
        val markedCurrentlyDeployed = repository.isCurrentlyDeployedTo(
          deliveryConfig = deliveryConfig,
          artifact = artifact,
          version = event.artifactVersion,
          targetEnvironment = env.name
        )
        if (!markedCurrentlyDeployed) {
          log.info("Marking {} as deployed in {} for config {} because it is already deployed", event.artifactVersion, env.name, deliveryConfig.name)
          repository.markAsSuccessfullyDeployedTo(
            deliveryConfig = deliveryConfig,
            artifact = artifact,
            version = event.artifactVersion,
            targetEnvironment = env.name
          )
        }
      }
    }
}
