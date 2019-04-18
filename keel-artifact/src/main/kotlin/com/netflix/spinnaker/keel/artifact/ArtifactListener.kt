package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifactVersion
import com.netflix.spinnaker.keel.events.ArtifactEvent
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.net.URI

@Component
class ArtifactListener(
  private val artifactRepository: ArtifactRepository
) {
  @EventListener(ArtifactEvent::class)
  fun onArtifactEvent(event: ArtifactEvent) {
    log.info("Received artifact event: {}", event)
    event.artifacts.forEach {
      val registeredArtifact = artifactRepository.get(it.name, ArtifactType.valueOf(it.type))
      if (registeredArtifact != null) {
        artifactRepository.store(DeliveryArtifactVersion(
          registeredArtifact,
          it.version,
          it.provenance.let(::URI)
        ))
      }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
