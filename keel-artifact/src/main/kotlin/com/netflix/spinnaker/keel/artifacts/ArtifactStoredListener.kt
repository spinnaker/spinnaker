package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactSaved
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ArtifactStoredListener(
  private val repository: KeelRepository,
  private val artifactSuppliers: List<ArtifactSupplier<*, *>>
) {

  @EventListener(ArtifactSaved::class)
  fun onArtifactSaved(event: ArtifactSaved) {
    val artifactSupplier = artifactSuppliers.supporting(event.artifact.type)
    runBlocking {
      try {
        val artifactMetadata = artifactSupplier.getArtifactMetadata(event.artifact)
        //TODO[gyardeni]: remove this statement when done debugging
        log.debug("received artifact metadata $artifactMetadata from artifactSupplier for name $event.artifact.name and version $event.artifact.version")

        if (artifactMetadata != null) {
          log.debug("storing artifact metadata for name $event.artifact.name and version $event.artifact.version")
          repository.updateArtifactMetadata(event.artifact.name, event.artifact.type, event.artifact.version, event.artifactStatus, artifactMetadata)
        }
      } catch (ex: Exception) {
          log.error("Could not fetch artifact metadata for name $event.artifact.name and version $event.artifact.version", ex)
      }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

}
