package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.persistence.ResourceRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ResourceHistoryListener(private val resourceRepository: ResourceRepository) {

  @EventListener(ResourceEvent::class)
  fun onResourceEvent(event: ResourceEvent) {
    // TODO: don't record ResourceValid
    resourceRepository.appendHistory(event)
  }
}
