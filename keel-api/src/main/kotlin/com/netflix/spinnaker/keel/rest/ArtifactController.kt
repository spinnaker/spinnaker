package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.events.ArtifactEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/artifacts"])
class ArtifactController(
  private val publisher: ApplicationEventPublisher
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
}

data class EchoArtifactEvent(
  val payload: ArtifactEvent,
  val eventName: String
)
