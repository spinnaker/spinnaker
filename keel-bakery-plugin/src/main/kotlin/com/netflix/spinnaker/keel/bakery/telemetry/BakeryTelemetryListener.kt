package com.netflix.spinnaker.keel.bakery.telemetry

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.bakery.artifact.BakeLaunched
import com.netflix.spinnaker.keel.bakery.artifact.ImageRegionMismatchDetected
import com.netflix.spinnaker.keel.bakery.artifact.RecurrentBakeDetected
import com.netflix.spinnaker.keel.bakery.constraint.MissingRegionsDetected
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class BakeryTelemetryListener(private val spectator: Registry) {

  @EventListener(ImageRegionMismatchDetected::class)
  fun onImageRegionMismatchDetected(event: ImageRegionMismatchDetected) {
    spectator.counter(
      IMAGE_REGION_MISMATCH_DETECTED_ID,
      listOf(
        BasicTag("appVersion", event.image.appVersion),
        BasicTag("baseAmiVersion", event.image.baseAmiVersion),
        BasicTag("found", event.image.regions.joinToString()),
        BasicTag("desired", event.regions.joinToString())
      )
    )
      .runCatching { increment() }
      .onFailure {
        log.error("Exception incrementing {} counter: {}", IMAGE_REGION_MISMATCH_DETECTED_ID, it.message)
      }
  }

  @EventListener(BakeLaunched::class)
  fun onBakeLaunched(event: BakeLaunched) {
    spectator.counter(
      BAKE_LAUNCHED_ID,
      listOf(
        BasicTag("appVersion", event.appVersion)
      )
    )
      .runCatching { increment() }
      .onFailure {
        log.error("Exception incrementing {} counter: {}", BAKE_LAUNCHED_ID, it.message)
      }
  }

  @EventListener(RecurrentBakeDetected::class)
  fun onRecurrentBakeDetected(event: RecurrentBakeDetected) {
    spectator.counter(
      RECURRENT_BAKE_DETECTED_ID,
      listOf(
        BasicTag("versions", "${event.appVersion}+${event.baseAmiVersion}")
      )
    )
  }

  @EventListener(MissingRegionsDetected::class)
  fun onMissingRegionsDetected(event: MissingRegionsDetected) {
    spectator.counter(
      MISSING_REGIONS_DETECTED,
      listOf(
        BasicTag("versions", event.version)
      )
    )
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    private const val IMAGE_REGION_MISMATCH_DETECTED_ID = "keel.bakery.image.region.mismatch"
    private const val BAKE_LAUNCHED_ID = "keel.bakery.bake.launched"
    private const val RECURRENT_BAKE_DETECTED_ID = "keel.bakery.recurrent.bake.detected"
    private const val MISSING_REGIONS_DETECTED = "keel.bakery.missing.regions.detected"
  }
}
