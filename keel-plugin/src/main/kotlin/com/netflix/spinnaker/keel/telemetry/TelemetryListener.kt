package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Counter
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

  @EventListener(LockAttempt::class)
  fun onLockAttempt(event: LockAttempt) {
    spectator.counter(
      LOCK_ATTEMPT_COUNTER_ID,
      listOf(BasicTag("success", event.success.toString()))
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

  private fun Counter.safeIncrement() =
    try {
      increment()
    } catch (ex: Exception) {
      log.error("Exception incrementing {} counter: {}", id().name(), ex.message)
    }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    private const val RESOURCE_CHECKED_COUNTER_ID = "keel.resource.checked"
    private const val LOCK_ATTEMPT_COUNTER_ID = "keel.lock.attempt"
    private const val ARTIFACT_UPDATED_COUNTER_ID = "keel.artifact.updated"
  }
}
