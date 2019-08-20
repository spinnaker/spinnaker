package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.events.ArtifactEvent
import com.netflix.spinnaker.keel.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.persistence.ArtifactAlreadyRegistered
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.ExceptionHandler
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
  private val artifactRepository: ArtifactRepository
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  @PostMapping(
    path = ["/events"],
    consumes = [APPLICATION_JSON_VALUE]
  )
  @ResponseStatus(ACCEPTED)
  fun submitArtifact(@RequestBody echoArtifactEvent: EchoArtifactEvent) {
    log.debug(
      "Received artifact events {} for {}",
      echoArtifactEvent.eventName,
      echoArtifactEvent.payload.artifacts.map { it.name }
    )
    publisher.publishEvent(echoArtifactEvent.payload)
  }

  @PostMapping(
    consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @ResponseStatus(CREATED)
  fun register(@RequestBody artifact: DeliveryArtifact) {
    log.debug("Registering {} artifact {}", artifact.type, artifact.name)
    publisher.publishEvent(ArtifactRegisteredEvent(artifact))
  }

  @GetMapping(
    path = ["/{name}/{type}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun listVersions(
    @PathVariable name: String,
    @PathVariable type: ArtifactType
  ): List<String> =
    artifactRepository.versions(DeliveryArtifact(name, type))

  @ExceptionHandler(NoSuchArtifactException::class)
  @ResponseStatus(NOT_FOUND)
  fun onNotFound(e: NoSuchArtifactException) {
    log.error(e.message)
  }

  @ExceptionHandler(ArtifactAlreadyRegistered::class)
  @ResponseStatus(CONFLICT)
  fun onAlreadyRegistered(e: ArtifactAlreadyRegistered) {
    log.error(e.message)
  }
}

data class EchoArtifactEvent(
  val payload: ArtifactEvent,
  val eventName: String
)
