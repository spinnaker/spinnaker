package com.netflix.spinnaker.keel.api.titus.image

import com.netflix.spinnaker.keel.api.ArtifactType.docker
import com.netflix.spinnaker.keel.api.matchingArtifact
import com.netflix.spinnaker.keel.api.titus.cluster.TitusClusterSpec
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class CurrentlyDeployedDockerImageApprover(
  private val artifactRepository: ArtifactRepository,
  private val resourceRepository: ResourceRepository,
  private val deliveryConfigRepository: DeliveryConfigRepository
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @EventListener(ArtifactVersionDeployed::class)
  fun onArtifactVersionDeployed(event: ArtifactVersionDeployed) =
    runBlocking {
      val resourceId = event.resourceId
      val resource = resourceRepository.get(resourceId)
      val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(resourceId)
      val env = deliveryConfigRepository.environmentFor(resourceId)

      (resource.spec as? TitusClusterSpec)?.let { spec ->
        if (spec.defaults.container != null && spec.defaults.container is ReferenceProvider) {
          val container = spec.defaults.container as ReferenceProvider
          val artifact = deliveryConfig.matchingArtifact(container.reference, docker)

          val approvedForEnv = artifactRepository.isApprovedFor(
            deliveryConfig = deliveryConfig,
            artifact = artifact,
            version = event.artifactVersion,
            targetEnvironment = env.name
          )
          // should only mark as successfully deployed if it's already approved for the environment
          if (approvedForEnv) {
            val wasSuccessfullyDeployed = artifactRepository.wasSuccessfullyDeployedTo(
              deliveryConfig = deliveryConfig,
              artifact = artifact,
              version = event.artifactVersion,
              targetEnvironment = env.name
            )
            if (!wasSuccessfullyDeployed) {
              log.info("Marking {} as deployed in {} for config {} because it is already deployed", event.artifactVersion, env.name, deliveryConfig.name)
              artifactRepository.markAsSuccessfullyDeployedTo(
                deliveryConfig = deliveryConfig,
                artifact = artifact,
                version = event.artifactVersion,
                targetEnvironment = env.name
              )
            }
          }
        }
      }
    }
}
