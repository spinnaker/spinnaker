package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.patterns.PolledMeter
import com.netflix.spinnaker.keel.actuation.ResourceCheckCompleted
import com.netflix.spinnaker.keel.actuation.ScheduledArtifactCheckStarting
import com.netflix.spinnaker.keel.actuation.ScheduledEnvironmentCheckStarting
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceCheckResult
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Component
class TelemetryListener(
  private val spectator: Registry,
  private val clock: Clock
) {
  private val lastResourceCheck: AtomicReference<Instant> =
    createDriftGauge(RESOURCE_CHECK_DRIFT_GAUGE)
  private val lastEnvironmentCheck: AtomicReference<Instant> =
    createDriftGauge(ENVIRONMENT_CHECK_DRIFT_GAUGE)
  private val lastArtifactCheck: AtomicReference<Instant> =
    createDriftGauge(ARTIFACT_CHECK_DRIFT_GAUGE)

  @EventListener(ResourceCheckResult::class)
  fun onResourceChecked(event: ResourceCheckResult) {
    spectator.counter(
      RESOURCE_CHECKED_COUNTER_ID,
      listOf(
        BasicTag("resourceId", event.id),
        BasicTag("resourceKind", event.kind.toString()),
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
        BasicTag("resourceKind", event.kind.toString()),
        BasicTag("skipper", event.skipper)
      )
    ).safeIncrement()
  }

  @EventListener(ResourceCheckTimedOut::class)
  fun onResourceCheckTimedOut(event: ResourceCheckTimedOut) {
    spectator.counter(
      RESOURCE_CHECK_TIMED_OUT_ID,
      listOf(
        BasicTag("kind", event.kind.kind),
        BasicTag("resourceId", event.id),
        BasicTag("application", event.application)
      )
    ).safeIncrement()
  }

  @EventListener(ResourceLoadFailed::class)
  fun onResourceLoadFailed(event: ResourceLoadFailed) {
    spectator.counter(RESOURCE_LOAD_FAILED_ID).safeIncrement()
  }

  @EventListener(EnvironmentsCheckTimedOut::class)
  fun onEnvironmentsCheckTimedOut(event: EnvironmentsCheckTimedOut) {
    spectator.counter(
      ENVIRONMENT_CHECK_TIMED_OUT_ID,
      listOf(
        BasicTag("application", event.application),
        BasicTag("deliveryConfig", event.deliveryConfigName)
      )
    ).safeIncrement()
  }

  @EventListener(ArtifactVersionUpdated::class)
  fun onArtifactVersionUpdated(event: ArtifactVersionUpdated) {
    spectator.counter(
      ARTIFACT_UPDATED_COUNTER_ID,
      listOf(
        BasicTag("artifactName", event.name),
        BasicTag("artifactType", event.type)
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
        BasicTag("artifactType", event.artifactType)
      )
    ).safeIncrement()
  }

  @EventListener(ResourceActuationLaunched::class)
  fun onResourceActuationLaunched(event: ResourceActuationLaunched) {
    spectator.counter(
      RESOURCE_ACTUATION_LAUNCHED_COUNTER_ID,
      listOf(
        BasicTag("resourceId", event.id),
        BasicTag("resourceKind", event.kind.toString()),
        BasicTag("resourceApplication", event.application)
      )
    ).safeIncrement()
  }

  @EventListener(ResourceCheckCompleted::class)
  fun onResourceCheckCompleted(event: ResourceCheckCompleted) {
    lastResourceCheck.set(clock.instant())
  }

  @EventListener(ScheduledEnvironmentCheckStarting::class)
  fun onScheduledCheckStarting(event: ScheduledEnvironmentCheckStarting) {
    lastEnvironmentCheck.set(clock.instant())
  }

  @EventListener(ScheduledArtifactCheckStarting::class)
  fun onScheduledCheckStarting(event: ScheduledArtifactCheckStarting) {
    lastArtifactCheck.set(clock.instant())
  }

  @EventListener(ArtifactVersionVetoed::class)
  fun onArtifactVersionVetoed(event: ArtifactVersionVetoed) {
    spectator.counter(
      ARTIFACT_VERSION_VETOED,
      listOf(BasicTag("application", event.application))
    )
      .safeIncrement()
  }

  @EventListener(EnvironmentCheckComplete::class)
  fun onEnvironmentCheckComplete(event: EnvironmentCheckComplete) {
    spectator.timer(
      ENVIRONMENT_CHECK_DURATION_ID,
      listOf(BasicTag("application", event.application))
    ).record(event.duration)
  }

  @EventListener(VerificationCompleted::class)
  fun onVerificationCompleted(event: VerificationCompleted) {
    spectator.counter(
      VERIFICATION_COMPLETED_COUNTER_ID,
      listOf(
        BasicTag("application", event.application),
        BasicTag("verificationType", event.verificationType),
        BasicTag("status", event.status.name)
      )
    )
  }

  @EventListener(VerificationStarted::class)
  fun onVerificationStarted(event: VerificationStarted) {
    spectator.counter(
      VERIFICATION_STARTED_COUNTER_ID,
      listOf(
        BasicTag("application", event.application),
        BasicTag("verificationType", event.verificationType)
      )
    )
  }

  private fun createDriftGauge(name: String): AtomicReference<Instant> =
    PolledMeter
      .using(spectator)
      .withName(name)
      .monitorValue(AtomicReference(clock.instant())) {
        Duration
          .between(it.get(), clock.instant())
          .toMillis()
          .toDouble()
          .div(1000) // convert to seconds
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
    private const val RESOURCE_CHECK_TIMED_OUT_ID = "keel.resource.check.timeout"
    private const val RESOURCE_LOAD_FAILED_ID = "keel.resource.load.failed"
    private const val RESOURCE_ACTUATION_LAUNCHED_COUNTER_ID = "keel.resource.actuation.launched"
    private const val ARTIFACT_CHECK_DRIFT_GAUGE = "keel.artifact.check.drift"
    private const val ARTIFACT_UPDATED_COUNTER_ID = "keel.artifact.updated"
    private const val ARTIFACT_APPROVED_COUNTER_ID = "keel.artifact.approved"
    private const val RESOURCE_CHECK_DRIFT_GAUGE = "keel.resource.check.drift"
    private const val ENVIRONMENT_CHECK_DRIFT_GAUGE = "keel.environment.check.drift"
    private const val ENVIRONMENT_CHECK_TIMED_OUT_ID = "keel.environment.check.timeout"
    private const val ENVIRONMENT_CHECK_DURATION_ID = "keel.environment.check.duration"
    private const val ARTIFACT_VERSION_VETOED = "keel.artifact.version.vetoed"
    private const val VERIFICATION_COMPLETED_COUNTER_ID = "keel.verification.completed"
    private const val VERIFICATION_STARTED_COUNTER_ID = "keel.verification.started"
  }
}
