package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class TelemetryListener(
  private val spectator: Registry
) {
  private val resourceCheckedCounterId = Id.create("keel.resource.checked")
  private val scope = CoroutineScope(Dispatchers.IO)

  @EventListener(ResourceChecked::class)
  fun onResourceChecked(event: ResourceChecked) {
    scope.launch {
      try {
        spectator.counter(
          resourceCheckedCounterId
            .withTag("resourceName", event.name.value)
            .withTag("resourceState", event.state.name)
        ).increment()
      } catch (ex: Exception) {
        log.error("Exception incrementing Atlas counter: {}", ex.message)
      }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
