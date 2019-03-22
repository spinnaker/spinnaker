package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class TelemetryListener(
  private val spectator: Registry,
  private val instanceIdSupplier: InstanceIdSupplier
) {
  private val resourceCheckedCounterId = Id.create("keel.resource.checked")
  private val scope = CoroutineScope(Dispatchers.IO)

  @EventListener(ResourceChecked::class)
  fun onResourceChecked(event: ResourceChecked) {
    scope.launch {
      try {
        spectator.counter(
          resourceCheckedCounterId
            .withTag("resource_name", event.name.value)
            .withTag("resource_state", event.state.name)
            .withTag("instance_id", instanceIdSupplier.get())
        ).increment()
      } catch (ex: Exception) {
        log.error("Exception incrementing Atlas counter: {}", ex.message)
      }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
