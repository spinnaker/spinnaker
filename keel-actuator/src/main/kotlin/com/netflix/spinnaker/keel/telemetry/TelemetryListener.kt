package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.patterns.PolledMeter
import com.netflix.spinnaker.keel.actuation.ScheduledEnvironmentCheckStarting
import com.netflix.spinnaker.keel.actuation.ScheduledResourceCheckStarting
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceCheckResult
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class TelemetryListener(
  private val spectator: Registry,
  private val clock: Clock
) {
  private val lastResourceCheck: AtomicReference<Instant> = createDriftGauge(RESOURCE_CHECK_DRIFT_GAUGE)
  private val lastEnvironmentCheck: AtomicReference<Instant> = createDriftGauge(ENVIRONMENT_CHECK_DRIFT_GAUGE)

  @EventListener(ResourceCheckResult::class)
  fun onResourceChecked(event: ResourceCheckResult) {
    spectator.counter(
      RESOURCE_CHECKED_COUNTER_ID,
      listOf(
        BasicTag("resourceId", event.id),
        BasicTag("apiVersion", event.apiVersion),
        BasicTag("resourceKind", event.kind),
        BasicTag("resourceState", event.state.name),
        BasicTag("resourceApplication", event.application)
      )
    ).safeIncrement()
  }

  @EventListener(ResourceCheckSkipped::class)
  fun onResourceCheckSkipped(event: ResourceCheckSkipped) {
    spectator.counter(
      RESOURCE_CHECK_SKIPPED_COUNTER_ID,
      listOf(
        BasicTag("resourceId", event.id),
        BasicTag("apiVersion", event.apiVersion),
        BasicTag("resourceKind", event.kind),
        BasicTag("skipper", event.skipper)
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

  @EventListener(ArtifactVersionApproved::class)
  fun onArtifactVersionUpdated(event: ArtifactVersionApproved) {
    spectator.counter(
      ARTIFACT_APPROVED_COUNTER_ID,
      listOf(
        BasicTag("application", event.application),
        BasicTag("environment", event.environmentName),
        BasicTag("artifactName", event.artifactName),
        BasicTag("artifactType", event.artifactType.name)
      )
    ).safeIncrement()
  }

  @EventListener(ResourceActuationLaunched::class)
  fun onResourceActuationLaunched(event: ResourceActuationLaunched) {
    spectator.counter(
      RESOURCE_ACTUATION_LAUNCHED_COUNTER_ID,
      listOf(
        BasicTag("resourceId", event.id),
        BasicTag("apiVersion", event.apiVersion),
        BasicTag("resourceKind", event.kind),
        BasicTag("resourceApplication", event.application)
      )
    ).safeIncrement()
  }

  @EventListener(ScheduledResourceCheckStarting::class)
  fun onScheduledCheckStarting(event: ScheduledResourceCheckStarting) {
    lastResourceCheck.set(clock.instant())
  }

  @EventListener(ScheduledEnvironmentCheckStarting::class)
  fun onScheduledCheckStarting(event: ScheduledEnvironmentCheckStarting) {
    lastEnvironmentCheck.set(clock.instant())
  }

  private fun createDriftGauge(name: String): AtomicReference<Instant> {
    return PolledMeter
      .using(spectator)
      .withName(name)
      .monitorValue(AtomicReference(clock.instant())) {
        Duration
          .between(it.get(), clock.instant())
          .toMillis()
          .toDouble()
      }
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
    private const val RESOURCE_ACTUATION_LAUNCHED_COUNTER_ID = "keel.resource.actuation.launched"
    private const val ARTIFACT_UPDATED_COUNTER_ID = "keel.artifact.updated"
    private const val ARTIFACT_APPROVED_COUNTER_ID = "keel.artifact.approved"
    private const val RESOURCE_CHECK_DRIFT_GAUGE = "keel.resource.check.drift"
    private const val ENVIRONMENT_CHECK_DRIFT_GAUGE = "keel.environment.check.drift"
  }
}
