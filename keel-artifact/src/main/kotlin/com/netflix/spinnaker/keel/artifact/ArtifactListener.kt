package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.events.ArtifactEvent
import com.netflix.spinnaker.keel.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionUpdated
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ArtifactListener(
  private val artifactRepository: ArtifactRepository,
  private val artifactService: ArtifactService,
  private val publisher: ApplicationEventPublisher
) {
  @EventListener(ArtifactEvent::class)
  fun onArtifactEvent(event: ArtifactEvent) {
    log.info("Received artifact event: {}", event)
    event
      .artifacts
      .filter { it.type.toUpperCase() in artifactTypeNames }
      .forEach {
        val artifact = it.toDeliveryArtifact()
        // TODO: should be able to construct this with Frigga or something, apparently, also it might
        //  make sense to have a method that does this on the Kork class rather than here
        val version = "${it.name}-${it.version}"
        if (artifactRepository.isRegistered(artifact.name, artifact.type)) {
          log.info("Registering version {} of {} {}", version, artifact.name, artifact.type)
          artifactRepository.store(artifact, version)
            .also { wasAdded ->
              if (wasAdded) {
                publisher.publishEvent(ArtifactVersionUpdated(artifact.name, artifact.type))
              }
            }
        }
      }
  }

  @EventListener(ArtifactRegisteredEvent::class)
  fun onArtifactRegisteredEvent(event: ArtifactRegisteredEvent) {
    val artifact = event.artifact
    if (artifactRepository.isRegistered(artifact.name, artifact.type)) {
      log.debug("Artifact {} is already registered", artifact)
      return
    }
    artifactRepository.register(artifact)
    runBlocking {
      artifactService
        .getVersions(artifact.name)
        .firstOrNull()
        ?.let { firstVersion ->
          val version = "${artifact.name}-$firstVersion"
          log.debug("Registering latest version {} for newly registered artifact {}", version, artifact)
          artifactRepository.store(artifact, version)
        }
    }
  }

  private val artifactTypeNames by lazy { ArtifactType.values().map(ArtifactType::name) }

  private fun Artifact.toDeliveryArtifact(): DeliveryArtifact =
    DeliveryArtifact(name, ArtifactType.valueOf(type.toUpperCase()))

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
