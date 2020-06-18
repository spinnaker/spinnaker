package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType.deb
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType.docker
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.api.events.ArtifactSyncEvent
import com.netflix.spinnaker.keel.artifact.events.KorkArtifactEvent
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import com.netflix.spinnaker.kork.artifacts.model.Artifact
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
  private val publisher: ApplicationEventPublisher,
  private val repository: KeelRepository
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @PostMapping(
    path = ["/events"],
    consumes = [APPLICATION_JSON_VALUE]
  )
  @ResponseStatus(ACCEPTED)
  fun submitArtifact(@RequestBody echoArtifactEvent: EchoArtifactEvent) {
    echoArtifactEvent.payload.artifacts.forEach { artifact ->
      if (artifact.type.equals(deb.toString(), true) && artifact.isFromArtifactEvent()) {
        publisher.publishEvent(KorkArtifactEvent(listOf(artifact), emptyMap()))
      } else if (artifact.type.equals(docker.toString(), true)) {
        publisher.publishEvent(KorkArtifactEvent(listOf(artifact), emptyMap()))
      } else {
        log.debug("Ignoring artifact event with type {}: {}", artifact.type, artifact)
      }
    }
  }

  @PostMapping(
    path = ["/register"],
    consumes = [APPLICATION_JSON_VALUE]
  )
  @ResponseStatus(ACCEPTED)
  fun register(@RequestBody artifact: DeliveryArtifact) {
    repository.register(artifact)
    publisher.publishEvent(ArtifactRegisteredEvent(artifact))
  }

  @PostMapping(
    path = ["/sync"]
  )
  @ResponseStatus(ACCEPTED)
  fun sync() {
    publisher.publishEvent(ArtifactSyncEvent(true))
  }

  @GetMapping(
    path = ["/{name}/{type}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun listVersions(
    @PathVariable name: String,
    @PathVariable type: ArtifactType
  ): List<String> =
    repository.artifactVersions(name, type)

  // Debian Artifacts should contain a releaseStatus in the metadata
  private fun Artifact.isFromArtifactEvent() =
    this.metadata.containsKey("releaseStatus") && this.metadata["releaseStatus"] != null
}

data class EchoArtifactEvent(
  val payload: KorkArtifactEvent,
  val eventName: String
)
