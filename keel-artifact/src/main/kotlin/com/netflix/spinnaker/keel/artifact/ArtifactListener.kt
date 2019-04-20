package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifactVersion
import com.netflix.spinnaker.keel.events.ArtifactEvent
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionUpdated
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.net.URI

@Component
class ArtifactListener(
  private val artifactRepository: ArtifactRepository,
  private val publisher: ApplicationEventPublisher
) {
  @EventListener(ArtifactEvent::class)
  fun onArtifactEvent(event: ArtifactEvent) {
    log.info("Received artifact event: {}", event)
    event.artifacts.forEach {
      DeliveryArtifactVersion(
        DeliveryArtifact(it.name, ArtifactType.valueOf(it.type)),
        it.version,
        it.provenance.let(URI::create)
      )
        .apply {
          if (artifactRepository.isRegistered(artifact.name, artifact.type)) {
            log.info("Registering version {} of {} {}", version, artifact.name, artifact.type)
            artifactRepository.store(this)
              .also { wasAdded ->
                if (wasAdded) {
                  publisher.publishEvent(ArtifactVersionUpdated(artifact.name, artifact.type))
                }
              }
          }
        }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
