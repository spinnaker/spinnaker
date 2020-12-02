package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.events.ArtifactPublishedEvent
import com.netflix.spinnaker.keel.api.events.ArtifactSyncEvent
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.api.plugins.UnsupportedArtifactException
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.igor.artifact.ArtifactMetadataService
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/artifacts"])
class ArtifactController(
  private val eventPublisher: ApplicationEventPublisher,
  private val repository: KeelRepository,
  private val artifactSuppliers: List<ArtifactSupplier<*, *>>,
  private val artifactMetadataService: ArtifactMetadataService
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @PostMapping(
    path = ["/events"],
    consumes = [APPLICATION_JSON_VALUE]
  )
  @ResponseStatus(ACCEPTED)
  fun submitArtifact(@RequestBody echoArtifactEvent: EchoArtifactEvent) {
    log.debug("Received artifact event: $echoArtifactEvent")
    echoArtifactEvent.payload.artifacts.forEach { artifact ->
      try {
        log.debug("Processing artifact from event: $artifact")
        val artifactSupplier = artifactSuppliers.supporting(artifact.type.toLowerCase())
        log.debug("Publishing artifact ${artifact.name} version ${artifact.version} via ${artifactSupplier::class.simpleName}")
        artifactSupplier.publishArtifact(artifact)
      } catch (e: UnsupportedArtifactException) {
        log.debug("Ignoring artifact event with unsupported type {}: {}", artifact.type, artifact)
      }
    }
  }

  @PostMapping(
    path = ["/sync"]
  )
  @ResponseStatus(ACCEPTED)
  fun sync() {
    eventPublisher.publishEvent(ArtifactSyncEvent(true))
  }

  // This endpoint is calling Igor (and then the CI provider) under the covers.
  @GetMapping(
    path = ["/build/{buildNumber}/commit/{commitId}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun getArtifactMetadata(
    @PathVariable buildNumber: String,
    @PathVariable commitId: String
  ): ArtifactMetadata? =

     try {
       runBlocking {
         artifactMetadataService.getArtifactMetadata(buildNumber, commitId)
       }
    } catch (ex: Exception) {
      log.error("failed to get artifact metadata for build $buildNumber and commit $commitId", ex)
      null
    }
}

data class EchoArtifactEvent(
  val payload: ArtifactPublishedEvent,
  val eventName: String
)
