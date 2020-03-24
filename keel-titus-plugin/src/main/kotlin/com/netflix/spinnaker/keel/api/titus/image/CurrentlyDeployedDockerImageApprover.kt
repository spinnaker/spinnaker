package com.netflix.spinnaker.keel.api.titus.image

import com.netflix.spinnaker.keel.api.artifacts.ArtifactType.docker
import com.netflix.spinnaker.keel.api.titus.cluster.TitusClusterSpec
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoMatchingArtifactException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class CurrentlyDeployedDockerImageApprover(
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

      (resource.spec as? TitusClusterSpec)?.let { spec ->
        if (spec.defaults.container != null && spec.defaults.container is ReferenceProvider) {
          val container = spec.defaults.container as ReferenceProvider
          val artifact = deliveryConfig.matchingArtifactByReference(container.reference, docker)
            ?: throw NoMatchingArtifactException(deliveryConfig.name, docker, container.reference)

          val approvedForEnv = repository.isApprovedFor(
            deliveryConfig = deliveryConfig,
            artifact = artifact,
            version = event.artifactVersion,
            targetEnvironment = env.name
          )
          // should only mark as successfully deployed if it's already approved for the environment
          if (approvedForEnv) {
            val wasSuccessfullyDeployed = repository.wasSuccessfullyDeployedTo(
              deliveryConfig = deliveryConfig,
              artifact = artifact,
              version = event.artifactVersion,
              targetEnvironment = env.name
            )
            if (!wasSuccessfullyDeployed) {
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
    }
}
