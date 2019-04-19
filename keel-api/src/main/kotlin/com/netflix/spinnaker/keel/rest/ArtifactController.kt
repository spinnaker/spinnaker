package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.events.ArtifactEvent
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
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

  @PostMapping(consumes = [APPLICATION_JSON_VALUE])
  fun submitArtifact(@RequestBody echoArtifactEvent: EchoArtifactEvent) {
    log.debug(
      "Received artifact events {} for {}",
      echoArtifactEvent.eventName,
      echoArtifactEvent.payload.artifacts.map { it.reference }
    )
    publisher.publishEvent(echoArtifactEvent.payload)
  }

  @PostMapping(path = ["/register"], consumes = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE])
  @ResponseStatus(CREATED)
  fun register(@RequestBody artifact: DeliveryArtifact) {
    if (artifactRepository.isRegistered(artifact.name, artifact.type)) {
      throw ArtifactAlreadyRegistered("Delivery artifact ${artifact.name} of type ${artifact.type} is already registered")
    }
    artifactRepository.store(artifact)
  }
}

data class EchoArtifactEvent(
  val payload: ArtifactEvent,
  val eventName: String
)

@ResponseStatus(CONFLICT)
class ArtifactAlreadyRegistered(message: String) : RuntimeException(message)
