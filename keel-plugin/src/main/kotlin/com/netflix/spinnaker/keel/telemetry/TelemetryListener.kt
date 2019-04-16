package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.BasicTag
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
        RESOURCE_CHECKED_COUNTER_ID,
        listOf(
          BasicTag("resourceName", event.name.value),
          BasicTag("apiVersion", event.apiVersion.toString()),
          BasicTag("resourceKind", event.kind),
          BasicTag("resourceState", event.state.name)
        )
      ).increment()
    } catch (ex: Exception) {
      log.error("Exception incrementing Atlas counter: {}", ex.message)
    }
  }

  @EventListener(LockAttempt::class)
  fun onLockAttempt(event: LockAttempt) {
    try {
      spectator.counter(
        LOCK_ATTEMPT_COUNTER_ID,
        listOf(BasicTag("success", event.success.toString()))
      ).increment()
    } catch (ex: Exception) {
      log.error("Exception incrementing Atlas counter: {}", ex.message)
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    private const val RESOURCE_CHECKED_COUNTER_ID = "keel.resource.checked"
    private const val LOCK_ATTEMPT_COUNTER_ID = "keel.lock.attempt"
  }
}
