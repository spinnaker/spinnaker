package com.netflix.spinnaker.keel.lifecycle

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Receives lifecycle events and stores them
 */
@Component
class LifecycleEventHandler(
  val repository: LifecycleEventRepository
) {

  @EventListener(LifecycleEvent::class)
  fun handleEvent(event: LifecycleEvent) {
    repository.saveEvent(event)
  }
}
