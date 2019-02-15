package com.netflix.spinnaker.keel.bus

import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceEventType.CREATE
import com.netflix.spinnaker.keel.events.ResourceEventType.DELETE
import com.netflix.spinnaker.keel.events.ResourceEventType.UPDATE
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ResourceEventListener(
  private val resourceRepository: ResourceRepository
) {

  @EventListener
  fun handle(event: ResourceEvent) {
    log.info("Received event {}", event)
    when (event.type) {
      CREATE,
      UPDATE ->
        resourceRepository.store(event.resource)
      DELETE ->
        resourceRepository.delete(event.resource.metadata.name)
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
