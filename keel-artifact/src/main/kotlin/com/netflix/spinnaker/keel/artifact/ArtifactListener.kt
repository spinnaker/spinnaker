package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.ArtifactStatus
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
    log.debug("Received artifact event: {}", event)
    event
      .artifacts
      .filter { it.type.toUpperCase() in artifactTypeNames }
      .forEach { korkArtifact ->
        val artifact = korkArtifact.toDeliveryArtifact()
        if (artifactRepository.isRegistered(artifact.name, artifact.type)) {
          val version = "${korkArtifact.name}-${korkArtifact.version}"
          val status = artifactStatus(korkArtifact)
          log.info("Registering version {} ({}) of {} {}", version, status, artifact.name, artifact.type)
          artifactRepository.store(artifact, version, status)
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
    } else {
      log.debug("Registering artifact {}", artifact)
      artifactRepository.register(artifact)
    }

    if (artifactRepository.versions(artifact).isEmpty()) {
      storeLatestVersion(artifact, event.statuses)
    }
  }

  /**
   * Grab the latest version which matches the statuses we care about, so the artifact is relevant.
   */
  protected fun storeLatestVersion(artifact: DeliveryArtifact, statuses: List<ArtifactStatus>) =
    runBlocking {
      artifactService
        .getVersions(artifact.name, statuses)
        .firstOrNull()
        ?.let { firstVersion ->
          val version = "${artifact.name}-$firstVersion"
          val status = artifactStatus(artifactService.getArtifact(artifact.name, firstVersion))
          log.debug("Storing latest version {} ({}) for registered artifact {}", version, status, artifact)
          artifactRepository.store(artifact, version, status)
        }
    }

  /**
   * Parses the status from a kork artifact, and throws an error if [releaseStatus] isn't
   * present in [metadata]
   */
  private fun artifactStatus(artifact: Artifact): ArtifactStatus {
    val status = artifact.metadata["releaseStatus"]?.toString()
      ?: throw IllegalStateException("Artifact event received without 'releaseStatus' field")
    return ArtifactStatus.valueOf(status)
  }

  private val artifactTypeNames by lazy { ArtifactType.values().map(ArtifactType::name) }

  private fun Artifact.toDeliveryArtifact(): DeliveryArtifact =
    DeliveryArtifact(name, ArtifactType.valueOf(type.toUpperCase()))

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
