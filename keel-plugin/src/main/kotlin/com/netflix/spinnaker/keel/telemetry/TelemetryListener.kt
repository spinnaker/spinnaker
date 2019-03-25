package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class TelemetryListener(
  private val spectator: Registry
) {

  @EventListener(ResourceChecked::class)
  fun onResourceChecked(event: ResourceChecked) {
    try {
      spectator.counter(
        RESOURCE_CHECKED_COUNTER_ID
          .withTag("resourceName", event.name.value)
          .withTag("apiVersion", event.apiVersion.toString())
          .withTag("resourceKind", event.kind)
          .withTag("resourceState", event.state.name)
      ).increment()
    } catch (ex: Exception) {
      log.error("Exception incrementing Atlas counter: {}", ex.message)
    }
  }

  @EventListener(LockAttempt::class)
  fun onLockAttempt(event: LockAttempt) {
    try {
      spectator.counter(
        LOCK_ATTEMPT_COUNTER_ID
          .withTag("success", event.success)
      ).increment()
    } catch (ex: Exception) {
      log.error("Exception incrementing Atlas counter: {}", ex.message)
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    private val RESOURCE_CHECKED_COUNTER_ID = Id.create("keel.resource.checked")
    private val LOCK_ATTEMPT_COUNTER_ID = Id.create("keel.lock.attempt")
  }
}
