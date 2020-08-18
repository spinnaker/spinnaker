package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.persistence.ResourceRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ApplicationEventListener(private val resourceRepository: ResourceRepository) {
  @EventListener(ApplicationEvent::class)
  fun onResourceEvent(event: ApplicationEvent) {
    resourceRepository.appendHistory(event)
  }
}
