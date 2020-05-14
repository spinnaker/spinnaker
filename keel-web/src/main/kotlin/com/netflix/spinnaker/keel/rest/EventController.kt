package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.events.ResourceHistoryEvent
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository.Companion.DEFAULT_MAX_EVENTS
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.slf4j.LoggerFactory.getLogger
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/resources/events"])
class EventController(
  private val resourceRepository: ResourceRepository
) {
  private val log by lazy { getLogger(javaClass) }

  // TODO: move this to ResourceController since the request path belongs there
  @GetMapping(
    path = ["/{id}"],
    produces = [APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  @PreAuthorize("""@authorizationSupport.hasApplicationPermission('READ', 'RESOURCE', #id)
    and @authorizationSupport.hasCloudAccountPermission('READ', 'RESOURCE', #id)"""
  )
  fun eventHistory(
    @PathVariable("id") id: String,
    @RequestParam("limit") limit: Int?
  ): List<ResourceHistoryEvent> {
    log.debug("Getting state history for: $id")
    return resourceRepository.eventHistory(id, limit ?: DEFAULT_MAX_EVENTS)
  }
}
