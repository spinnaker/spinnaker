package com.netflix.spinnaker.keel.bakery.telemetry

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.bakery.artifact.BakeLaunched
import com.netflix.spinnaker.keel.bakery.artifact.ImageRegionMismatchDetected
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

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    private const val IMAGE_REGION_MISMATCH_DETECTED_ID = "keel.bakery.image.region.mismatch"
    private const val BAKE_LAUNCHED_ID = "keel.bakery.bake.launched"
  }
}
