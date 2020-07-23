package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.events.ArtifactVersionDeploying
import com.netflix.spinnaker.keel.persistence.KeelRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ArtifactDeployingListener(
  private val repository: KeelRepository
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @EventListener(ArtifactVersionDeploying::class)
  fun onArtifactVersionDeploying(event: ArtifactVersionDeploying) =
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
        log.info("Marking {} as deploying in {} for config {}", event.artifactVersion, env.name, deliveryConfig.name)
        repository.markAsDeployingTo(
          deliveryConfig = deliveryConfig,
          artifact = artifact,
          version = event.artifactVersion,
          targetEnvironment = env.name
        )
      } else {
        log.warn(
          "Somehow {} is deploying in {} for config {} without being approved for that environment",
          event.artifactVersion,
          env.name,
          deliveryConfig.name
        )
      }
    }
}
