package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.actuation.ScheduledResourceCheckStarting
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.annotation.PostConstruct

@Component
class TelemetryListener(
  private val spectator: Registry,
  private val clock: Clock
) {
  private var lastResourceCheck: Instant = clock.instant()

  @PostConstruct
  fun registerMeters() {
    spectator.gauge(RESOURCE_CHECK_DRIFT_GAUGE, this) {
      Duration
        .between(lastResourceCheck, clock.instant())
        .toMillis()
        .toDouble()
    }
  }

  @EventListener(ResourceChecked::class)
  fun onResourceChecked(event: ResourceChecked) {
    spectator.counter(
      RESOURCE_CHECKED_COUNTER_ID,
      listOf(
        BasicTag("resourceName", event.name.value),
        BasicTag("apiVersion", event.apiVersion.toString()),
        BasicTag("resourceKind", event.kind),
        BasicTag("resourceState", event.state.name)
      )
    ).safeIncrement()
  }

  @EventListener(ResourceCheckSkipped::class)
  fun onResourceCheckSkipped(event: ResourceCheckSkipped) {
    spectator.counter(
      RESOURCE_CHECK_SKIPPED_COUNTER_ID,
      listOf(
        BasicTag("resourceName", event.name.value),
        BasicTag("apiVersion", event.apiVersion.toString()),
        BasicTag("resourceKind", event.kind)
      )
    ).safeIncrement()
  }

  @EventListener(ArtifactVersionUpdated::class)
  fun onArtifactVersionUpdated(event: ArtifactVersionUpdated) {
    spectator.counter(
      ARTIFACT_UPDATED_COUNTER_ID,
      listOf(
        BasicTag("artifactName", event.name),
        BasicTag("artifactType", event.type.name)
      )
    ).safeIncrement()
  }

  @EventListener(ScheduledResourceCheckStarting::class)
  fun onScheduledCheckStarting(event: ScheduledResourceCheckStarting) {
    lastResourceCheck = clock.instant()
  }

  private fun Counter.safeIncrement() =
    try {
      increment()
    } catch (ex: Exception) {
      log.error("Exception incrementing {} counter: {}", id().name(), ex.message)
    }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    private const val RESOURCE_CHECKED_COUNTER_ID = "keel.resource.checked"
    private const val RESOURCE_CHECK_SKIPPED_COUNTER_ID = "keel.resource.check.skipped"
    private const val ARTIFACT_UPDATED_COUNTER_ID = "keel.artifact.updated"
    private const val RESOURCE_CHECK_DRIFT_GAUGE = "keel.resource.check.drift"
  }
}
